/*
 * Copyright (c) 2008-2021, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.jet.sql.impl.aggregate;

import com.hazelcast.function.FunctionEx;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.jet.sql.impl.processors.JetSqlRow;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;

import java.io.IOException;
import java.util.Arrays;

/**
 * A wrapper for {@code Data[]} supporting equals/hashCode.
 */
public final class ObjectArrayKey implements DataSerializable {

    private Data[] keyFields;

    @SuppressWarnings("unused")
    private ObjectArrayKey() {
    }

    private ObjectArrayKey(Data[] keyFields) {
        this.keyFields = keyFields;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        // TODO write Data specially?
        out.writeObject(keyFields);
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        keyFields = in.readObject();
    }

    @Override
    public String toString() {
        return "ObjectArray{" +
                "array=" + Arrays.toString(keyFields) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ObjectArrayKey that = (ObjectArrayKey) o;
        return Arrays.equals(keyFields, that.keyFields);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(keyFields);
    }

    /**
     * Return a function that maps an input {@link JetSqlRow} to an {@link
     * ObjectArrayKey}, extracting the fields given in {@code indices}.
     *
     * @param indices the indices of keys
     * @return the projection function
     */
    public static FunctionEx<JetSqlRow, ObjectArrayKey> projectFn(int[] indices) {
        return row -> {
            Data[] key = new Data[indices.length];
            for (int i = 0; i < indices.length; i++) {
                // TODO [viliam]
                key[i] = row.getSerialized(null, i);
            }
            return new ObjectArrayKey(key);
        };
    }
}