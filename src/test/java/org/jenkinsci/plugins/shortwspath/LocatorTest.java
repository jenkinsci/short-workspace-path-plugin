/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.shortwspath;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import hudson.matrix.AxisList;
import hudson.matrix.LabelExpAxis;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.DumbSlave;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

import jenkins.model.Jenkins;

import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

public class LocatorTest {

    public static final String DS = File.separator;

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void doNothingIfThereIsEnoughRoom() throws Exception {
        DumbSlave s = j.createOnlineSlave();

        MockFolder f = j.createFolder("this_is_my_folder_alright");
        FreeStyleProject p = f.createProject(FreeStyleProject.class, "and_a_project_in_it");
        p.setAssignedNode(s);

        // Enough for the test - even on windows
        setMaxPathLength(s, 4096);

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        assertThat(b.getWorkspace().getRemote(), equalTo(s.getRootPath() + DS + "workspace" + DS + p.getFullName().replace("/", DS)));
    }

    @Test
    public void doNothingIfItWillNotShortenThePath() throws Exception {
        DumbSlave s = j.createOnlineSlave();

        // Would be turned into 'short_project...XXXXXXXX' which is in fact longer than the original
        FreeStyleProject p = j.createFreeStyleProject("short_project_name");
        p.setAssignedNode(s);

        // Not enough for anything
        setMaxPathLength(s, 1);

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        assertThat(b.getWorkspace().getRemote(), equalTo(s.getRootPath() + DS + "workspace" + DS + p.getFullName().replace("/", DS)));
    }

    @Test
    public void unwrapFolders() throws Exception {
        DumbSlave s = j.createOnlineSlave();

        MockFolder f = j.createFolder("this_is_my_folder_alright");
        FreeStyleProject p = f.createProject(FreeStyleProject.class, "and_a_project_in_it");
        p.setAssignedNode(s);

        // Not enough for anything
        setMaxPathLength(s, 1);

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        String buildWs = b.getWorkspace().getRemote();
        String wsDir = s.getRootPath() + DS + "workspace" + DS;
        assertThat(buildWs, startsWith(wsDir + "and_a_pro"));
        assertThat(buildWs, buildWs.length(), equalTo(wsDir.length() + 24));
    }

    @Test
    public void shortenMatrix() throws Exception {
        Node slave = j.createOnlineSlave();
        setMaxPathLength(slave, 1); // Not enough for anything

        MatrixProject mp = j.createMatrixProject();
        mp.setAssignedNode(slave);
        mp.setAxes(new AxisList(new LabelExpAxis("axis", slave.getNodeName())));

        MatrixBuild build = j.buildAndAssertSuccess(mp);
        assertThat(build.getBuiltOn(), equalTo(slave));
        MatrixRun run = build.getExactRuns().get(0);
        assertThat(run.getBuiltOn(), equalTo(slave));

        System.out.println(build.getWorkspace());
        System.out.println(run.getWorkspace());
    }

    private void setMaxPathLength(Node s, int length) {
        ShortWsLocator locator = Jenkins.getInstance().getExtensionList(ShortWsLocator.class).get(0);
        try {
            Field f = ShortWsLocator.class.getDeclaredField("cachedMaxLengths");
            f.setAccessible(true);
            Map<Node, Integer> map = (Map<Node, Integer>) f.get(locator);
            map.put(s, length);
        } catch (NoSuchFieldException ex) {
            throw new AssertionError(ex);
        } catch (SecurityException ex) {
            throw new AssertionError(ex);
        } catch (IllegalArgumentException ex) {
            throw new AssertionError(ex);
        } catch (IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
    }
}
