package org.apache.cassandra.io;
/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.cassandra.db.ColumnFamily;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.io.util.BufferedRandomAccessFile;
import org.apache.cassandra.io.util.FileRangeDataInput;
import org.apache.cassandra.service.StorageService;

public class IteratingRow implements Comparable<IteratingRow>
{
    private final DecoratedKey key;
    private final long finishedAt;
    private final BufferedRandomAccessFile file;
    public final SSTableReader sstable;
    private long dataStart;
    private int dataSize;

    public IteratingRow(BufferedRandomAccessFile file, SSTableReader sstable) throws IOException
    {
        this.file = file;
        this.sstable = sstable;

        key = StorageService.getPartitioner().convertFromDiskFormat(file.readUTF());
        dataSize = file.readInt();
        dataStart = file.getFilePointer();
        finishedAt = dataStart + dataSize;
    }

    public DecoratedKey getKey()
    {
        return key;
    }

    public String getPath()
    {
        return file.getPath();
    }

    public void echoData(DataOutput out) throws IOException
    {
        file.seek(dataStart);
        while (file.getFilePointer() < finishedAt)
        {
            out.write(file.readByte());
        }
    }
    
    /**
     * @return the dataSize
     */
    public int getDataSize()
    {
        return dataSize;
    }
    
    public DataInput getDataInput() throws IOException
    {
        return new FileRangeDataInput(file, dataStart, finishedAt);
    }

    // TODO r/m this and make compaction merge columns iteratively for CASSSANDRA-16
    public ColumnFamily getColumnFamily() throws IOException
    {
        file.seek(dataStart);
        IndexHelper.skipBloomFilter(file);
        IndexHelper.skipIndex(file);
        return ColumnFamily.serializer().deserializeFromSSTable(sstable, file);
    }

    public void skipRemaining() throws IOException
    {
        file.seek(finishedAt);
    }

    public long getEndPosition()
    {
        return finishedAt;
    }

    public int compareTo(IteratingRow o)
    {
        return key.compareTo(o.key);
    }
}
