require 'spec_helper'

describe "rackup files don't have to reside at the root" do

  deploy <<-END.gsub(/^ {4}/,'')
    --- 
    application: 
      RACK_ROOT: #{File.dirname(__FILE__)}/../apps/rack/norootrackup
      RACK_ENV: development
    web: 
      rackup: foobar/config.ru
      context: /norootrackup
    
    ruby:
      version: #{RUBY_VERSION[0,3]}
  END

  it "should be happy" do
    visit "/norootrackup"
    root = File.expand_path( File.join( File.dirname( __FILE__ ), '..', '/apps/rack/norootrackup' ) )
    prefix = root.start_with?("/") ? "vfs:" : "vfs:/"
    #page.source.strip.downcase.should == "RACK_ROOT=#{prefix}#{root}".downcase
    page.should have_content( "RACK_ROOT=#{prefix}#{root}" )
  end

end
