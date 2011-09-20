/*
 * Copyright 2008-2011 Red Hat, Inc, and individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.torquebox.core.as;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.io.IOException;
import java.util.Hashtable;
import java.util.List;

import javax.management.MBeanServer;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.jmx.MBeanRegistrationService;
import org.jboss.as.jmx.MBeanServerService;
import org.jboss.as.jmx.ObjectNameFactory;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder.DependencyType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.stdio.StdioContext;

import org.torquebox.TorqueBox;
import org.torquebox.TorqueBoxMBean;
import org.torquebox.TorqueBoxStdioContextSelector;
import org.torquebox.core.ArchiveDirectoryMountingProcessor;
import org.torquebox.core.GlobalRuby;
import org.torquebox.core.GlobalRubyMBean;
import org.torquebox.core.TorqueBoxRbProcessor;
import org.torquebox.core.TorqueBoxYamlParsingProcessor;
import org.torquebox.core.app.AppJarScanningProcessor;
import org.torquebox.core.app.AppKnobYamlParsingProcessor;
import org.torquebox.core.app.ApplicationYamlParsingProcessor;
import org.torquebox.core.app.EnvironmentYamlParsingProcessor;
import org.torquebox.core.app.RubyApplicationDefaultsProcessor;
import org.torquebox.core.app.RubyApplicationDeployer;
import org.torquebox.core.app.RubyApplicationExploder;
import org.torquebox.core.app.RubyApplicationRecognizer;
import org.torquebox.core.app.RubyYamlParsingProcessor;
import org.torquebox.core.injection.CorePredeterminedInjectableDeployer;
import org.torquebox.core.injection.PredeterminedInjectableProcessor;
import org.torquebox.core.injection.analysis.InjectableHandlerRegistry;
import org.torquebox.core.injection.analysis.InjectionIndexingProcessor;
import org.torquebox.core.pool.PoolingYamlParsingProcessor;
import org.torquebox.core.runtime.BaseRubyRuntimeDeployer;
import org.torquebox.core.runtime.RubyRuntimeFactoryDeployer;
import org.torquebox.core.runtime.RuntimePoolDeployer;

class CoreSubsystemAdd extends AbstractBoottimeAddStepHandler {
    
    @Override
    protected void populateModel(ModelNode operation, ModelNode model) {
        model.get( "injector" ).setEmptyObject();
    }
    
    @Override
    protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model,
                                   ServiceVerificationHandler verificationHandler,
                                   List<ServiceController<?>> newControllers) throws OperationFailedException {
        
        final InjectableHandlerRegistry registry = new InjectableHandlerRegistry();
        
        context.addStep( new AbstractDeploymentChainStep() {
            @Override
            protected void execute(DeploymentProcessorTarget processorTarget) {
                addDeploymentProcessors( processorTarget, registry );
            }
        }, OperationContext.Stage.RUNTIME );
        
        try {
            addCoreServices( context, verificationHandler, newControllers, registry );
            addTorqueBoxStdioContext();
        } catch (Exception e) {
            throw new OperationFailedException( e, null );
        }
        
    }

    protected void addDeploymentProcessors(final DeploymentProcessorTarget processorTarget, final InjectableHandlerRegistry registry) {
        processorTarget.addDeploymentProcessor( Phase.STRUCTURE, 0, new KnobRootMountProcessor() );
        processorTarget.addDeploymentProcessor( Phase.STRUCTURE, 0, new KnobStructureProcessor() );
        processorTarget.addDeploymentProcessor( Phase.STRUCTURE, 20, new AppKnobYamlParsingProcessor() );
        processorTarget.addDeploymentProcessor( Phase.STRUCTURE, 100, new AppJarScanningProcessor() );

        processorTarget.addDeploymentProcessor( Phase.PARSE, 0, new RubyApplicationRecognizer() );
        processorTarget.addDeploymentProcessor( Phase.PARSE, 5, new TorqueBoxYamlParsingProcessor() );
        processorTarget.addDeploymentProcessor( Phase.PARSE, 10, new TorqueBoxRbProcessor() );
        processorTarget.addDeploymentProcessor( Phase.PARSE, 20, new ApplicationYamlParsingProcessor() );
        processorTarget.addDeploymentProcessor( Phase.PARSE, 30, new EnvironmentYamlParsingProcessor() );
        processorTarget.addDeploymentProcessor( Phase.PARSE, 35, new PoolingYamlParsingProcessor() );
        processorTarget.addDeploymentProcessor( Phase.PARSE, 36, new RubyYamlParsingProcessor() );
        processorTarget.addDeploymentProcessor( Phase.PARSE, 40, new RubyApplicationDefaultsProcessor() );
        processorTarget.addDeploymentProcessor( Phase.PARSE, 100, new RubyApplicationExploder() );
        processorTarget.addDeploymentProcessor( Phase.PARSE, 4000, new BaseRubyRuntimeDeployer() );

        processorTarget.addDeploymentProcessor( Phase.DEPENDENCIES, 0, new CoreDependenciesProcessor() );
        processorTarget.addDeploymentProcessor( Phase.DEPENDENCIES, 10, new JdkDependenciesProcessor() );
        processorTarget.addDeploymentProcessor( Phase.CONFIGURE_MODULE, 1000, new PredeterminedInjectableProcessor( registry ) );
        processorTarget.addDeploymentProcessor( Phase.CONFIGURE_MODULE, 1001, new CorePredeterminedInjectableDeployer() );
        processorTarget.addDeploymentProcessor( Phase.CONFIGURE_MODULE, 1100, new InjectionIndexingProcessor( registry ) );
        processorTarget.addDeploymentProcessor( Phase.POST_MODULE, 100, new ArchiveDirectoryMountingProcessor() );
        processorTarget.addDeploymentProcessor( Phase.INSTALL, 0, new RubyRuntimeFactoryDeployer() );
        processorTarget.addDeploymentProcessor( Phase.INSTALL, 10, new RuntimePoolDeployer() );
        processorTarget.addDeploymentProcessor( Phase.INSTALL, 1000, new DeploymentNotifierInstaller() );
        processorTarget.addDeploymentProcessor( Phase.INSTALL, 9000, new RubyApplicationDeployer() );
    }

    protected void addCoreServices(final OperationContext context, ServiceVerificationHandler verificationHandler,
                                   List<ServiceController<?>> newControllers, 
                                   InjectableHandlerRegistry registry) throws Exception {
        addTorqueBoxService( context, verificationHandler, newControllers, registry );
        //FIXME: disabled to speed up clojure only usage
        //addGlobalRubyServices( context, verificationHandler, newControllers, registry );
        addInjectionServices( context, verificationHandler, newControllers, registry );
    }

    
    @SuppressWarnings("serial")
    protected void addTorqueBoxService(final OperationContext context, ServiceVerificationHandler verificationHandler,
                                       List<ServiceController<?>> newControllers,
                                       InjectableHandlerRegistry registry) throws IOException {
    	TorqueBox torqueBox = new TorqueBox();
        torqueBox.printVersionInfo( log );
        torqueBox.verifyJRubyVersion( log );
        
        newControllers.add( context.getServiceTarget().addService( CoreServices.TORQUEBOX, torqueBox )
            .setInitialMode( Mode.ACTIVE )
            .addListener( verificationHandler )
            .install() );
        
        String mbeanName = ObjectNameFactory.create( "torquebox", new Hashtable<String, String>() {
            {
                put( "type", "version" );
            }
        } ).toString();

        MBeanRegistrationService<TorqueBoxMBean> mbeanService = new MBeanRegistrationService<TorqueBoxMBean>( mbeanName );
        newControllers.add( context.getServiceTarget().addService( CoreServices.TORQUEBOX.append( "mbean" ), mbeanService )
                .addDependency( DependencyType.OPTIONAL, MBeanServerService.SERVICE_NAME, MBeanServer.class, mbeanService.getMBeanServerInjector() )
                .addDependency( CoreServices.TORQUEBOX, TorqueBoxMBean.class, mbeanService.getValueInjector() )
                .addListener( verificationHandler )
                .setInitialMode( Mode.PASSIVE )
                .install() );
    }

    @SuppressWarnings("serial")
    protected void addGlobalRubyServices(final OperationContext context, ServiceVerificationHandler verificationHandler,
                                         List<ServiceController<?>> newControllers,
                                         InjectableHandlerRegistry registry) {
        newControllers.add( context.getServiceTarget().addService( CoreServices.GLOBAL_RUBY, new GlobalRuby() )
                .addListener( verificationHandler )
                .setInitialMode( Mode.ACTIVE )
                .install() );

        String mbeanName = ObjectNameFactory.create( "torquebox", new Hashtable<String, String>() {
            {
                put( "type", "runtime" );
            }
        } ).toString();

        MBeanRegistrationService<GlobalRubyMBean> mbeanService = new MBeanRegistrationService<GlobalRubyMBean>( mbeanName );
        newControllers.add( context.getServiceTarget().addService( CoreServices.GLOBAL_RUBY.append( "mbean" ), mbeanService )
                .addDependency( DependencyType.OPTIONAL, MBeanServerService.SERVICE_NAME, MBeanServer.class, mbeanService.getMBeanServerInjector() )
                .addDependency( CoreServices.GLOBAL_RUBY, GlobalRubyMBean.class, mbeanService.getValueInjector() )
                .addListener( verificationHandler )
                .setInitialMode( Mode.PASSIVE )
                .install() );
    }

    protected void addInjectionServices(final OperationContext context, ServiceVerificationHandler verificationHandler,
                                        List<ServiceController<?>> newControllers,
                                        InjectableHandlerRegistry registry) {
        newControllers.add( context.getServiceTarget().addService( CoreServices.INJECTABLE_HANDLER_REGISTRY, registry )
                .addListener( verificationHandler )
                .setInitialMode( Mode.PASSIVE )
                .install() );
    }

    protected void addTorqueBoxStdioContext() {
        // Grab the existing AS7 StdioContext
        final StdioContext defaultContext = StdioContext.getStdioContext();
        // Uninstall to reset System.in, .out, .err to default values
        StdioContext.uninstall();
        // Create debug StdioContext based on System streams
        final StdioContext debugContext = StdioContext.create( System.in, System.out, System.err );
        TorqueBoxStdioContextSelector selector = new TorqueBoxStdioContextSelector( defaultContext, debugContext );

        StdioContext.install();
        StdioContext.setStdioContextSelector( selector);
    }

    static ModelNode createOperation(ModelNode address) {
        final ModelNode subsystem = new ModelNode();
        subsystem.get( OP ).set( ADD );
        subsystem.get( OP_ADDR ).set( address );
        return subsystem;
    }

    public CoreSubsystemAdd() {
    }

    static final CoreSubsystemAdd ADD_INSTANCE = new CoreSubsystemAdd();
    static final Logger log = Logger.getLogger( "org.torquebox.core.as" );

}
