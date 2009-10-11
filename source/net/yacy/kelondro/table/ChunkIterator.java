// kelondroChunkIterator.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.01.2008 on http://yacy.net
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.kelondro.table;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;

public class ChunkIterator implements Iterator<byte[]> {

    private final int chunksize;
    
    /**
     * create a ChunkIterator
     * a ChunkIterator uses a BufferedInputStream to iterate through the file
     * and is therefore a fast option to get all elements in the file as a sequence
     * @param file: the file
     * @param recordsize: the size of the elements in the file
     * @param chunksize: the size of the chunks that are returned by next(). remaining bytes until the lenght of recordsize are skipped
     * @throws FileNotFoundException 
     */
    
    
    private final DataInputStream stream;
    private byte[] nextBytes;
    private int recordsize;
    
    public ChunkIterator(final File file, final int recordsize, final int chunksize) throws FileNotFoundException {
        assert (file.exists());
        assert file.length() % recordsize == 0;
        this.recordsize = recordsize;
        this.chunksize = chunksize;
        this.stream = new DataInputStream(new BufferedInputStream(new FileInputStream(file), 64 * 1024));
        this.nextBytes = next0();
    }
    
    public boolean hasNext() {
        return nextBytes != null;
    }

    public byte[] next0() {
        final byte[] chunk = new byte[chunksize];
        int r, s;
        try {
            // read the chunk
            this.stream.readFully(chunk);
            // skip remaining bytes
            r = chunksize;
            while (r < recordsize) {
                s = (int) this.stream.skip(recordsize - r);
                assert s > 0;
                if (s <= 0) return null;
                r += s;
            }
            return chunk;
        } catch (final IOException e) {
            return null;
        }
    }

    public byte[] next() {
        final byte[] n = this.nextBytes;
        this.nextBytes = next0();
        return n;
    }
    
    public void remove() {
        throw new UnsupportedOperationException();
    }
}