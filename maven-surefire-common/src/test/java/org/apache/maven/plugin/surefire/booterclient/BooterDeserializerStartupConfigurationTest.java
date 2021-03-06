package org.apache.maven.plugin.surefire.booterclient;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.apache.maven.surefire.booter.*;
import org.apache.maven.surefire.cli.CommandLineOption;
import org.apache.maven.surefire.report.ReporterConfiguration;
import org.apache.maven.surefire.testset.*;
import org.apache.maven.surefire.util.RunOrder;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.apache.maven.surefire.cli.CommandLineOption.LOGGING_LEVEL_DEBUG;
import static org.apache.maven.surefire.cli.CommandLineOption.REACTOR_FAIL_FAST;
import static org.apache.maven.surefire.cli.CommandLineOption.SHOW_ERRORS;

/**
 * Performs roundtrip testing of serialization/deserialization of The StartupConfiguration
 *
 * @author Kristian Rosenvold
 */
public class BooterDeserializerStartupConfigurationTest
    extends TestCase
{

    private static int idx = 0;

    private File basedir;

    private final ClasspathConfiguration classpathConfiguration = createClasspathConfiguration();

    private final List<CommandLineOption> cli =
        Arrays.asList( LOGGING_LEVEL_DEBUG, SHOW_ERRORS, REACTOR_FAIL_FAST );

    @Before
    public void setupDirectories() throws IOException
    {
        File target = new File( System.getProperty( "user.dir" ), "target" );
        basedir = new File( target, "BooterDeserializerProviderConfigurationTest-" + ++idx );
        FileUtils.deleteDirectory( basedir );
        assertTrue( basedir.mkdirs() );
    }

    @After
    public void deleteDirectories() throws IOException
    {
        FileUtils.deleteDirectory( basedir );
    }

    public void testProvider()
        throws IOException
    {
        assertEquals( "com.provider", getReloadedStartupConfiguration().getProviderClassName() );
    }

    public void testClassPathConfiguration()
        throws IOException
    {
        AbstractPathConfiguration reloadedClasspathConfiguration =
            getReloadedStartupConfiguration().getClasspathConfiguration();

        assertTrue( reloadedClasspathConfiguration instanceof ClasspathConfiguration );
        assertCpConfigEquals( classpathConfiguration, (ClasspathConfiguration) reloadedClasspathConfiguration );
    }

    private void assertCpConfigEquals( ClasspathConfiguration expectedConfiguration,
                               ClasspathConfiguration actualConfiguration )
    {
        assertEquals( expectedConfiguration.getTestClasspath().getClassPath(),
                      actualConfiguration.getTestClasspath().getClassPath() );
        assertEquals( expectedConfiguration.isEnableAssertions(), actualConfiguration.isEnableAssertions() );
        assertEquals(  expectedConfiguration.isChildDelegation(), actualConfiguration.isChildDelegation() );
        assertEquals(  expectedConfiguration.getProviderClasspath(), actualConfiguration.getProviderClasspath() );
        assertEquals(  expectedConfiguration.getTestClasspath(), actualConfiguration.getTestClasspath() );
    }

    public void testClassLoaderConfiguration()
        throws IOException
    {
        assertFalse( getReloadedStartupConfiguration().isManifestOnlyJarRequestedAndUsable() );
    }

    public void testClassLoaderConfigurationTrues()
        throws IOException
    {
        final StartupConfiguration testStartupConfiguration =
            getTestStartupConfiguration( getManifestOnlyJarForkConfiguration() );
        boolean current = testStartupConfiguration.isManifestOnlyJarRequestedAndUsable();
        assertEquals( current, saveAndReload( testStartupConfiguration ).isManifestOnlyJarRequestedAndUsable() );
    }

    private ClasspathConfiguration createClasspathConfiguration()
    {
        Classpath testClassPath = new Classpath( Arrays.asList( "CP1", "CP2" ) );
        Classpath providerClasspath = new Classpath( Arrays.asList( "SP1", "SP2" ) );
        return new ClasspathConfiguration( testClassPath, providerClasspath, Classpath.emptyClasspath(), true, true );
    }

    private static ClassLoaderConfiguration getSystemClassLoaderConfiguration()
    {
        return new ClassLoaderConfiguration( true, false );
    }

    private static ClassLoaderConfiguration getManifestOnlyJarForkConfiguration()
    {
        return new ClassLoaderConfiguration( true, true );
    }


    private StartupConfiguration getReloadedStartupConfiguration()
        throws IOException
    {
        ClassLoaderConfiguration classLoaderConfiguration = getSystemClassLoaderConfiguration();
        return saveAndReload( getTestStartupConfiguration( classLoaderConfiguration ) );
    }

    private StartupConfiguration saveAndReload( StartupConfiguration startupConfiguration )
        throws IOException
    {
        final ForkConfiguration forkConfiguration = ForkConfigurationTest.getForkConfiguration( basedir, null );
        PropertiesWrapper props = new PropertiesWrapper( new HashMap<String, String>() );
        BooterSerializer booterSerializer = new BooterSerializer( forkConfiguration );
        String aTest = "aTest";
        File propsTest = booterSerializer.serialize( props, getProviderConfiguration(), startupConfiguration, aTest,
                false, null, 1 );
        BooterDeserializer booterDeserializer = new BooterDeserializer( new FileInputStream( propsTest ) );
        assertNull( booterDeserializer.getPluginPid() );
        return booterDeserializer.getProviderConfiguration();
    }

    private ProviderConfiguration getProviderConfiguration()
    {

        File cwd = new File( "." );
        DirectoryScannerParameters directoryScannerParameters =
            new DirectoryScannerParameters( cwd, new ArrayList<String>(), new ArrayList<String>(),
                                            new ArrayList<String>(), true, "hourly" );
        ReporterConfiguration reporterConfiguration = new ReporterConfiguration( cwd, true );
        TestRequest testSuiteDefinition =
            new TestRequest( Arrays.asList( getSuiteXmlFileStrings() ), getTestSourceDirectory(),
                             new TestListResolver( "aUserRequestedTest#aUserRequestedTestMethod" ));

        RunOrderParameters runOrderParameters = new RunOrderParameters( RunOrder.DEFAULT, null );
        return new ProviderConfiguration( directoryScannerParameters, runOrderParameters, true, reporterConfiguration,
                new TestArtifactInfo( "5.0", "ABC" ), testSuiteDefinition, new HashMap<String, String>(),
                BooterDeserializerProviderConfigurationTest.aTestTyped, true, cli, 0, Shutdown.DEFAULT, 0 );
    }

    private StartupConfiguration getTestStartupConfiguration( ClassLoaderConfiguration classLoaderConfiguration )
    {
        return new StartupConfiguration( "com.provider", classpathConfiguration, classLoaderConfiguration, false,
                                         false );
    }

    private File getTestSourceDirectory()
    {
        return new File( "TestSrc" );
    }

    private String[] getSuiteXmlFileStrings()
    {
        return new String[]{ "A1", "A2" };
    }
}
