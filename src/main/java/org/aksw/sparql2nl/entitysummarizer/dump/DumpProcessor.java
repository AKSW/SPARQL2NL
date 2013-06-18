/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.dump;

import java.util.List;


/**
 *
 * @author ngonga
 */
public interface DumpProcessor {
    List<LogEntry> processDump(String file);
    List<LogEntry> processDump(String file, int limit);
}
