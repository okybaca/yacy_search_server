// ThreadDumpGenerator
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004-2010
//
// This File contains contributions from Alexander Fieger
//
// $LastChangedDate: 2010-09-02 21:24:22 +0200 (Do, 02 Sep 2010) $
// $LastChangedRevision: 7092 $
// $LastChangedBy: orbiter $
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

package net.yacy.kelondro.logging;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;

import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.util.FileUtils;
import de.anomic.tools.nxTools;

public class ThreadDumpGenerator {

    public static void appendStackTraces(final File rootPath, final StringBuilder buffer, final Map<Thread,StackTraceElement[]> stackTraces, final boolean plain, final Thread.State stateIn) {
        bufferappend(buffer, plain, "THREADS WITH STATES: " + stateIn.toString());
        bufferappend(buffer, plain, "");
        // collect single dumps
        HashMap<String, SortedSet<String>> dumps = dumpCollection(rootPath, stackTraces, plain, stateIn);
        
        // write dumps
        for (final Entry<String, SortedSet<String>> entry: dumps.entrySet()) {
            SortedSet<String> threads = entry.getValue();
            for (String t: threads) bufferappend(buffer, plain, t);
            bufferappend(buffer, plain, entry.getKey());
            bufferappend(buffer, plain, "");
        }
        bufferappend(buffer, plain, "");
    }
    
    public static void appendStackTraceStats(final File rootPath, final StringBuilder buffer, final ArrayList<Map<Thread,StackTraceElement[]>> traces, final boolean plain, final Thread.State stateIn) {
        if (stateIn != null) {
            bufferappend(buffer, plain, "THREADS WITH STATES: " + stateIn.toString());
            bufferappend(buffer, plain, "");
        }
        // collect single dumps
        HashMap<String, Integer> dumps = dumpStatistic(rootPath, traces, plain, stateIn);
        
        // write dumps
        while (!dumps.isEmpty()) {
            Entry<String, Integer> e = removeMax(dumps);
            bufferappend(buffer, plain, "Occurrences: " + e.getValue());
            bufferappend(buffer, plain, e.getKey());
            //bufferappend(buffer, plain, "");
        }
        bufferappend(buffer, plain, "");
    }
    
    private static Entry<String, Integer> removeMax(HashMap<String, Integer> result) {
        Entry<String, Integer> max = null;
        for (final Entry<String, Integer> e: result.entrySet()) {
            if (max == null || e.getValue().intValue() > max.getValue().intValue()) {
                max = e;
            }
        }
        result.remove(max.getKey());
        return max;
    }
    
    private static HashMap<String, Integer> dumpStatistic(final File rootPath, final ArrayList<Map<Thread,StackTraceElement[]>> stackTraces, final boolean plain, final Thread.State stateIn) {
        Map<Thread,StackTraceElement[]> trace;
        HashMap<String, Integer> result = new HashMap<String, Integer>();
        HashMap<String, SortedSet<String>> x;
        int count;
        for (int i = 0; i < stackTraces.size(); i++) {
            trace = stackTraces.get(i);
            x = dumpCollection(rootPath, trace, plain, stateIn);
            for (final Entry<String, SortedSet<String>> e: x.entrySet()) {
                Integer c = result.get(e.getKey());
                count = e.getValue().size();
                if (c == null) result.put(e.getKey(), Integer.valueOf(count));
                else {
                    c = Integer.valueOf(c.intValue() + count);
                    result.put(e.getKey(), c);
                }
            }
        }
        return result;
    }
    
    private static HashMap<String, SortedSet<String>> dumpCollection(final File appPath, final Map<Thread,StackTraceElement[]> stackTraces, final boolean plain, final Thread.State stateIn) {
        final File classPath = new File(appPath, "source");
  
        Thread thread;
        // collect single dumps
        HashMap<String, SortedSet<String>> dumps = new HashMap<String, SortedSet<String>>();
        for (final Entry<Thread, StackTraceElement[]> entry: stackTraces.entrySet()) {
            thread = entry.getKey();
            final StackTraceElement[] stackTraceElements = entry.getValue();
            StackTraceElement ste;
            String line;
            String tracename = "";
            File classFile;
            if ((stateIn == null || stateIn.equals(thread.getState())) && stackTraceElements.length > 0) {
                StringBuilder sb = new StringBuilder(3000);
                if (plain) {
                    classFile = getClassFile(classPath, stackTraceElements[stackTraceElements.length - 1].getClassName());
                    tracename = classFile.getName();
                    if (tracename.endsWith(".java")) tracename = tracename.substring(0, tracename.length() - 5);
                    if (tracename.length() > 20) tracename = tracename.substring(0, 20);
                    while (tracename.length() < 20) tracename = tracename + "_";
                    tracename = "[" + tracename + "] ";                
                }                
                String threadtitle = tracename + "Thread= " + thread.getName() + " " + (thread.isDaemon()?"daemon":"") + " id=" + thread.getId() + " " + thread.getState().toString();
                String className;
                boolean cutcore = true;
                for (int i = 0; i < stackTraceElements.length; i++) {
                    ste = stackTraceElements[i];
                    className = ste.getClassName();
                    if (cutcore && (className.startsWith("java.") || className.startsWith("sun."))) {
                        sb.setLength(0);
                        bufferappend(sb, plain, tracename + "at " + CharacterCoding.unicode2html(ste.toString(), true));
                    } else {
                        cutcore = false;
                        if (i == 0) {
                            line = getLine(getClassFile(classPath, className), ste.getLineNumber());
                        } else {
                            line = null;
                        }
                        if ((line != null) && (line.length() > 0)) {
                            bufferappend(sb, plain, tracename + "at " + CharacterCoding.unicode2html(ste.toString(), true) + " [" + line.trim() + "]");
                        } else {
                            bufferappend(sb, plain, tracename + "at " + CharacterCoding.unicode2html(ste.toString(), true));
                        }
                    }
                }
                String threaddump = sb.toString();
                SortedSet<String> threads = dumps.get(threaddump);
                if (threads == null) threads = new TreeSet<String>();
                threads.add(threadtitle);
                dumps.put(threaddump, threads);
            }
        }
        return dumps;
    }
    
    private static File getClassFile(final File sourcePath, final String classname) {
        final String classPath = classname.replace('.', '/') + ".java";
        final File file = new File(sourcePath, classPath);
        return file;
    }
    
    private static String getLine(final File file, final int line) {
        // find class
        if (!file.exists()) return "";
        try {
            final String lineString = nxTools.line(FileUtils.read(file), line);
            if (lineString == null) return "@ERROR";
            return lineString;
        } catch (final IOException e) {
            return "@EXCEPTION: " + e.getMessage();
        }
    }
    
    public static void bufferappend(final StringBuilder buffer, final boolean plain, final String a) {
        buffer.append(a);
        if (plain) {
            buffer.append("\n");
        } else {
            buffer.append("<br />");
        }
    }
    
}