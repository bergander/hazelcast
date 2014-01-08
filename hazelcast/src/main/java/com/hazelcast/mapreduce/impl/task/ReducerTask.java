/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.mapreduce.impl.task;

import com.hazelcast.mapreduce.JobPartitionState;
import com.hazelcast.mapreduce.JobProcessInformation;
import com.hazelcast.mapreduce.Reducer;
import com.hazelcast.mapreduce.impl.MapReduceService;
import com.hazelcast.mapreduce.impl.MapReduceUtil;
import com.hazelcast.mapreduce.impl.operation.RequestPartitionProcessed;
import com.hazelcast.mapreduce.impl.operation.RequestPartitionResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReducerTask<Key, Chunk> implements Runnable {

    private final JobSupervisor supervisor;
    private final Queue<ReducerChunk<Key, Chunk>> reducerQueue;
    private final String name;
    private final String jobId;

    private AtomicBoolean active = new AtomicBoolean();

    public ReducerTask(String name, String jobId, JobSupervisor supervisor) {
        this.name = name;
        this.jobId = jobId;
        this.supervisor = supervisor;
        this.reducerQueue = new ConcurrentLinkedQueue<ReducerChunk<Key, Chunk>>();
    }

    public String getName() {
        return name;
    }

    public String getJobId() {
        return jobId;
    }

    public void processChunk(Map<Key, Chunk> chunk) {
        processChunk(-1, chunk);
    }

    public void processChunk(int partitionId, Map<Key, Chunk> chunk) {
        reducerQueue.offer(new ReducerChunk<Key, Chunk>(chunk, partitionId));
        if (active.compareAndSet(false, true)) {
            MapReduceService mapReduceService = supervisor.getMapReduceService();
            ExecutorService es = mapReduceService.getExecutorService(name);
            es.submit(this);
        }
    }

    private void reduceChunk(Map<Key, Chunk> chunk) {
        for (Map.Entry<Key, Chunk> entry : chunk.entrySet()) {
            Reducer reducer = supervisor.getReducerByKey(entry.getKey());
            if (reducer != null) {
                reducer.reduce(entry.getValue());
            }
        }
    }

    private <Value> Map<Key, Value> getReducedResults() {
        Map<Key, Value> reducedResults = new HashMap<Key, Value>();
        Map<Key, Reducer> reducers = supervisor.getReducers();
        for (Map.Entry<Key, Reducer> entry : reducers.entrySet()) {
            reducedResults.put(entry.getKey(), (Value) entry.getValue().finalizeReduce());
        }
        return reducedResults;
    }

    @Override
    public void run() {
        try {
            ReducerChunk<Key, Chunk> reducerChunk;
            while ((reducerChunk = reducerQueue.poll()) != null) {
                reduceChunk(reducerChunk.chunk);
                processProcessedState(reducerChunk);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            active.compareAndSet(true, false);
        }
    }

    private void processProcessedState(ReducerChunk<Key, Chunk> reducerChunk) {
        if (reducerChunk.partitionId != -1) {
            MapReduceService mapReduceService = supervisor.getMapReduceService();
            JobProcessInformationImpl processInformation = supervisor.getJobProcessInformation();
            try {
                RequestPartitionResult result = mapReduceService.processRequest(supervisor.getJobOwner(),
                        new RequestPartitionProcessed(name, jobId, reducerChunk.partitionId));
                if (result.getState() == RequestPartitionResult.State.SUCCESSFUL) {
                    if (checkFullyProcessed(processInformation)) {
                        // TODO Request reduced results
                    }
                }
            } catch (Exception ignore) {
                ignore.printStackTrace();
            }
        }
    }

    private boolean checkFullyProcessed(JobProcessInformation processInformation) {
        if (!supervisor.isOwnerNode()) {
            return false;
        }
        JobPartitionState[] partitionStates = processInformation.getPartitionStates();
        //System.out.println("Finished: " + MapReduceUtil.printPartitionStates(partitionStates));
        for (JobPartitionState partitionState : partitionStates) {
            if (partitionState == null
                    || partitionState.getState() == JobPartitionState.State.PROCESSED) {
                return false;
            }
        }
        return true;
    }

}
