/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.execution.scheduler;

import com.facebook.presto.client.NodeVersion;
import com.facebook.presto.execution.MockRemoteTaskFactory;
import com.facebook.presto.execution.RemoteTask;
import com.facebook.presto.metadata.PrestoNode;
import com.facebook.presto.spi.Node;
import com.facebook.presto.spi.PrestoException;
import com.google.common.collect.ImmutableList;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.facebook.presto.spi.StandardErrorCode.NO_NODES_AVAILABLE;
import static com.facebook.presto.util.ImmutableCollectors.toImmutableSet;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestFixedCountScheduler
{
    private final ExecutorService executor = newCachedThreadPool(daemonThreadsNamed("stageExecutor-%s"));
    private final MockRemoteTaskFactory taskFactory;

    public TestFixedCountScheduler()
    {
        taskFactory = new MockRemoteTaskFactory(executor);
    }

    @AfterClass
    public void destroyExecutor()
    {
        executor.shutdownNow();
    }

    @Test
    public void testSingleNode()
            throws Exception
    {
        FixedCountScheduler nodeScheduler = new FixedCountScheduler(
                node -> taskFactory.createTableScanTask((Node) node, ImmutableList.of()),
                TestFixedCountScheduler::generateRandomNodes,
                1);

        ScheduleResult result = nodeScheduler.schedule();
        assertTrue(result.isFinished());
        assertTrue(result.getBlocked().isDone());
        assertEquals(result.getNewTasks().size(), 1);
        result.getNewTasks().iterator().next().getNodeId().equals("other 0");
    }

    @Test
    public void testMultipleNodes()
            throws Exception
    {
        FixedCountScheduler nodeScheduler = new FixedCountScheduler(
                node -> taskFactory.createTableScanTask((Node) node, ImmutableList.of()),
                TestFixedCountScheduler::generateRandomNodes,
                5);

        ScheduleResult result = nodeScheduler.schedule();
        assertTrue(result.isFinished());
        assertTrue(result.getBlocked().isDone());
        assertEquals(result.getNewTasks().size(), 5);
        assertEquals(result.getNewTasks().stream().map(RemoteTask::getNodeId).collect(toImmutableSet()).size(), 5);
    }

    @Test
    public void testNotEnoughNodes()
            throws Exception
    {
        FixedCountScheduler nodeScheduler = new FixedCountScheduler(
                node -> taskFactory.createTableScanTask((Node) node, ImmutableList.of()),
                count -> generateRandomNodes(3),
                5);

        ScheduleResult result = nodeScheduler.schedule();
        assertTrue(result.isFinished());
        assertTrue(result.getBlocked().isDone());
        assertEquals(result.getNewTasks().size(), 3);
        assertEquals(result.getNewTasks().stream().map(RemoteTask::getNodeId).collect(toImmutableSet()).size(), 3);
    }

    @Test
    public void testNoNodes()
            throws Exception
    {
        try {
            FixedCountScheduler nodeScheduler = new FixedCountScheduler(
                    node -> taskFactory.createTableScanTask((Node) node, ImmutableList.of()),
                    count -> generateRandomNodes(0),
                    5);

            nodeScheduler.schedule();
            fail("expected PrestoException");
        }
        catch (PrestoException e) {
            assertEquals(e.getErrorCode(), NO_NODES_AVAILABLE.toErrorCode());
        }
    }

    private static List<Node> generateRandomNodes(int count)
    {
        ImmutableList.Builder<Node> nodes = ImmutableList.builder();
        for (int i = 0; i < count; i++) {
            nodes.add(new PrestoNode("other " + i, URI.create("http://127.0.0.1:11"), NodeVersion.UNKNOWN));
        }
        return nodes.build();
    }
}
