/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Map.Entry;

import org.dllearner.kb.sparql.SparqlEndpoint;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Multimap;

/**
 * @author Lorenz Buehmann
 *
 */
public class EntitySummarizationTest {
	
	SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpediaLiveAKSW();
	String queryLog = "resources/dbpediaLog/dbpedia.log-valid-select.gz";
	DumpProcessor dumpProcessor = new DBpediaDumpProcessor();
	Collection<LogEntry> logEntries;
	int maxNrOfLogEntries = -1;//-1 means load all entries
	EntitySummarizationModelGenerator generator = new EntitySummarizationModelGenerator(endpoint);

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		if(maxNrOfLogEntries == -1){
			logEntries = dumpProcessor.processDump(queryLog);
		} else {
			logEntries = dumpProcessor.processDump(queryLog, maxNrOfLogEntries);
		}
	}

	/**
	 * Test method for {@link org.aksw.sparql2nl.entitysummarizer.EntitySummarizationModelGenerator#generateModel(java.util.Collection)}.
	 */
	@Test
	public void testGenerateModel() {
		EntitySummarizationModel model = generator.generateModel(logEntries);
		System.out.println(model);
	}
	
	/**
	 * Test method for {@link org.aksw.sparql2nl.entitysummarizer.EntitySummarizationModelGenerator#generateModel(java.util.Collection)},
	 * but this time generating the model for each user agent occurring in the query log dump
	 */
	@Test
	public void testGenerateModelByUserAgent() {
		Multimap<String, LogEntry> groupedByUserAgent = LogEntryGrouping.groupByUserAgent(logEntries);
		
		//generate an entity summarization model for each user agent that occurs in the query log dump
		for (Entry<String, Collection<LogEntry>> entry : groupedByUserAgent.asMap().entrySet()) {
			String userAgent = entry.getKey();
			Collection<LogEntry> entries = entry.getValue();
			
			EntitySummarizationModel model = generator.generateModel(entries);
			System.out.println(userAgent + "\n" + model);
		}
	}

}
