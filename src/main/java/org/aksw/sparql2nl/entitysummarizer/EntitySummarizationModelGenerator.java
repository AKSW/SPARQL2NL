/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.aksw.sparql2nl.entitysummarizer.clustering.Node;
import org.aksw.sparql2nl.entitysummarizer.clustering.WeightedGraph;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.core.owl.Property;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;

/**
 * @author Lorenz Buehmann
 *
 */
public class EntitySummarizationModelGenerator {

	private SparqlEndpoint endpoint;
	private SPARQLQueryProcessor processor;

	/**
	 * 
	 */
	public EntitySummarizationModelGenerator(SparqlEndpoint endpoint) {
		this.endpoint = endpoint;
		processor = new SPARQLQueryProcessor(endpoint);
	}
	
	
	/**
	 * Generates a entity summarization model given a collection of SPARQL query log entries. 
	 * @param logEntries
	 * @return the entity summarization model
	 */
	public EntitySummarizationModel generateModel(Collection<LogEntry> logEntries){
		Set<EntitySummarizationTemplate> templates = new HashSet<EntitySummarizationTemplate>();
        
        //process the log entries
        Collection<Map<NamedClass, Set<Property>>> result = processor.processEntries(logEntries);
        
        //generate for each class in the knowledge base a summarization template
        for(NamedClass nc : new SPARQLReasoner(new SparqlEndpointKS(endpoint)).getOWLClasses()){
        	//generate the weighted graph
       	 	WeightedGraph wg = Controller.generateGraphMultithreaded(nc, result);
       	 	//generate the entity summarization template
       	 	Set<Property> properties = new HashSet<Property>();
       	 	for (Entry<Node, Double> entry : wg.getNodes().entrySet()) {
				properties.add(new ObjectProperty(entry.getKey().label));
			}
       	 	templates.add(new EntitySummarizationTemplate(nc, properties));
        }
        return new EntitySummarizationModel(templates);
	}

}
