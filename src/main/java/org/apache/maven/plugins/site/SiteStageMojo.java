package org.apache.maven.plugins.site;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.util.Iterator;

import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.model.Site;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.UnsupportedProtocolException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.observers.Debug;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.plexus.util.PathTool;
import org.codehaus.plexus.util.StringUtils;

/**
 * Staging a site in specific directory.
 * <p>Useful to test the generated site.</p>
 *
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @version $Id$
 * @goal stage
 * @requiresDependencyResolution test
 */
public class SiteStageMojo
    extends SiteMojo
{
    /**
     * Staging directory location.
     *
     * @parameter expression="${stagingDirectory}"
     * @required
     */
    private File stagingDirectory;

    /**
     * Staging site URL to deploy the staging directory.
     *
     * @see <a href="http://maven.apache.org/maven-model/maven.html#class_site">MavenModel#class_site</a>
     *
     * @parameter expression="${stagingSiteURL}"
     */
    private String stagingSiteURL;

    /**
     * @component
     */
    private WagonManager wagonManager;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        String structureProject = getStructure( project );

        outputDirectory = new File( stagingDirectory, structureProject );

        String outputRelativePath = PathTool.getRelativePath( stagingDirectory.getAbsolutePath(),
                                                              new File( outputDirectory, "dummy.html" )
                                                                  .getAbsolutePath() );
        project.setUrl( outputRelativePath + "/" + structureProject );

        if ( project.getParent() != null )
        {
            String structureParentProject = getStructure( project.getParent() );
            project.getParent().setUrl( outputRelativePath + "/" + structureParentProject );
        }

        if ( ( reactorProjects != null ) && ( reactorProjects.size() > 1 ) )
        {
            Iterator reactorItr = reactorProjects.iterator();

            while ( reactorItr.hasNext() )
            {
                MavenProject reactorProject = (MavenProject) reactorItr.next();

                if ( ( reactorProject != null ) && ( reactorProject.getParent() != null )
                    && ( project.getArtifactId().equals( reactorProject.getParent().getArtifactId() ) ) )
                {
                    String structureReactorProject = getStructure( reactorProject );
                    reactorProject.setUrl( outputRelativePath + "/" + structureReactorProject );
                }
            }
        }

        super.execute();

        if ( !StringUtils.isEmpty( stagingSiteURL ) )
        {
            deployStagingSite();
        }

    }

    /**
     * Generates the site structure using the project hiearchy (project and its modules) or using the
     * distributionManagement elements from the pom.xml.
     *
     * @param project
     * @return the structure relative path
     * @throws MojoExecutionException
     */
    private static String getStructure( MavenProject project )
        throws MojoExecutionException
    {
        if ( project.getDistributionManagement() == null )
        {
            String hierarchy = project.getName();

            MavenProject parent = project.getParent();
            while ( parent != null )
            {
                hierarchy = parent.getName() + "/" + hierarchy;
                parent = parent.getParent();
            }

            return hierarchy;
        }

        Site site = project.getDistributionManagement().getSite();
        if ( site == null )
        {
            throw new MojoExecutionException(
                                              "Missing site information in the distribution management element in the project." );
        }

        if ( StringUtils.isEmpty( site.getUrl() ) )
        {
            throw new MojoExecutionException( "The URL in the site is missing in the project descriptor." );
        }

        Repository repository = new Repository( site.getId(), site.getUrl() );
        if ( StringUtils.isEmpty( repository.getBasedir() ) )
        {
            return repository.getHost();
        }

        if ( repository.getBasedir().startsWith( "/" ) )
        {
            return repository.getHost() + repository.getBasedir();
        }

        return repository.getHost() + "/" + repository.getBasedir();
    }

    /**
     * Deploy the staging directory using the stagingSiteURL.
     *
     * @throws MojoExecutionException if any
     */
    private void deployStagingSite()
        throws MojoExecutionException
    {
        String id = "stagingSite";
        Repository repository = new Repository( id, stagingSiteURL );

        Wagon wagon = null;
        try
        {
            wagon = wagonManager.getWagon( repository.getProtocol() );
        }
        catch ( UnsupportedProtocolException e )
        {
            throw new MojoExecutionException( "Unsupported protocol: '" + repository.getProtocol() + "'", e );
        }

        if ( !wagon.supportsDirectoryCopy() )
        {
            throw new MojoExecutionException( "Wagon protocol '" + repository.getProtocol()
                + "' doesn't support directory copying" );
        }

        try
        {
            Debug debug = new Debug();

            wagon.addSessionListener( debug );

            wagon.addTransferListener( debug );

            wagon.connect( repository, wagonManager.getAuthenticationInfo( id ) );

            wagon.putDirectory( stagingDirectory, "." );
        }
        catch ( ResourceDoesNotExistException e )
        {
            throw new MojoExecutionException( "Error uploading site", e );
        }
        catch ( TransferFailedException e )
        {
            throw new MojoExecutionException( "Error uploading site", e );
        }
        catch ( AuthorizationException e )
        {
            throw new MojoExecutionException( "Error uploading site", e );
        }
        catch ( ConnectionException e )
        {
            throw new MojoExecutionException( "Error uploading site", e );
        }
        catch ( AuthenticationException e )
        {
            throw new MojoExecutionException( "Error uploading site", e );
        }
        finally
        {
            try
            {
                wagon.disconnect();
            }
            catch ( ConnectionException e )
            {
                getLog().error( "Error disconnecting wagon - ignored", e );
            }
        }
    }
}