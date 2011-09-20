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

package org.torquebox.clojure.web.as;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.List;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceController;
import org.torquebox.clojure.web.ring.RingApplicationRecognizer;
import org.torquebox.clojure.web.ring.RingWebApplicationDeployer;

class WebSubsystemAdd extends AbstractBoottimeAddStepHandler {
    
    @Override
    protected void populateModel(ModelNode operation, ModelNode model) {
        model.setEmptyObject();
    }
    
    @Override
    protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model,
                                   ServiceVerificationHandler verificationHandler,
                                   List<ServiceController<?>> newControllers) throws OperationFailedException {
        
        context.addStep( new AbstractDeploymentChainStep() {
            @Override
            protected void execute(DeploymentProcessorTarget processorTarget) {
                addDeploymentProcessors( processorTarget );
            }
        }, OperationContext.Stage.RUNTIME );
        
    }

    protected void addDeploymentProcessors(final DeploymentProcessorTarget processorTarget) {
        processorTarget.addDeploymentProcessor( Phase.PARSE, 0, new RingApplicationRecognizer() );
        //processorTarget.addDeploymentProcessor( Phase.PARSE, 10, new RailsApplicationRecognizer() );
        //processorTarget.addDeploymentProcessor( Phase.PARSE, 30, new WebYamlParsingProcessor() );
        //processorTarget.addDeploymentProcessor( Phase.PARSE, 40, new RailsVersionProcessor() );
        //processorTarget.addDeploymentProcessor( Phase.PARSE, 50, new RailsRackProcessor() );
        //processorTarget.addDeploymentProcessor( Phase.PARSE, 60, new RackApplicationDefaultsProcessor() );
        processorTarget.addDeploymentProcessor( Phase.PARSE, 70, new RingWebApplicationDeployer() );
        //processorTarget.addDeploymentProcessor( Phase.PARSE, 1000, new RailsRuntimeProcessor() );
        //processorTarget.addDeploymentProcessor( Phase.PARSE, 1100, new RackRuntimeProcessor() );
        
        processorTarget.addDeploymentProcessor( Phase.DEPENDENCIES, 1, new WebDependenciesProcessor() );
        
        //processorTarget.addDeploymentProcessor( Phase.CONFIGURE_MODULE, 100, new WebRuntimePoolProcessor() );
        //processorTarget.addDeploymentProcessor( Phase.CONFIGURE_MODULE, 500, new RailsAutoloadPathProcessor() );
        
        //processorTarget.addDeploymentProcessor( Phase.POST_MODULE, 120, new RackApplicationComponentResolverInstaller() );
        //processorTarget.addDeploymentProcessor( Phase.INSTALL, 2100, new VirtualHostInstaller() );
    }


    static ModelNode createOperation(ModelNode address) {
        final ModelNode subsystem = new ModelNode();
        subsystem.get( OP ).set( ADD );
        subsystem.get( OP_ADDR ).set( address );
        return subsystem;
    }

    static final WebSubsystemAdd ADD_INSTANCE = new WebSubsystemAdd();
    static final Logger log = Logger.getLogger( "org.torquebox.clojure.web.as" );

}
