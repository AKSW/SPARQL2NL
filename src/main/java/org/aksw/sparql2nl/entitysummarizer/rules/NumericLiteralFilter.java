/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer.rules;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import org.aksw.commons.util.Pair;
import org.aksw.jena_sparql_api.cache.extra.CacheFrontend;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.sparql2nl.naturallanguagegeneration.URIConverter;
import org.dllearner.kb.sparql.SparqlEndpoint;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.graph.impl.LiteralLabel;

/**
 * Returns only canonical forms of numeric literals, e.g. for 800 cm and 8.00m the digits are the same.
 * @author Lorenz Buehmann
 *
 */
public class NumericLiteralFilter {
	
	private URIConverter conv;
	
	public NumericLiteralFilter(SparqlEndpoint endpoint, CacheFrontend frontend) {
		conv = new URIConverter(endpoint, frontend);
	}

	public NumericLiteralFilter(SparqlEndpoint endpoint) {
		conv = new URIConverter(endpoint);
	}
//	
//	public NumericLiteralFilter(QueryExecutionFactory qef, String cacheDirectory) {
//		conv = new URIConverter(qef, cacheDirectory);
//	}
	
	
	public void filter(Set<Triple> triples){
		//find triples with same subject and predicate where the object is a literal
		Multimap<Pair<Node, String>, Triple> sp2Triples = HashMultimap.create();
		for (Triple t : triples) {
			if(t.getPredicate().isURI() && t.getObject().isLiteral() && isNumeric(t.getObject())){
				sp2Triples.put(new Pair<Node, String>(t.getSubject(), conv.convert(t.getPredicate().getURI())), t);
			}
		}
		Set<Triple> triples2Remove = new HashSet<Triple>();
		
		for (Entry<Pair<Node, String>, Collection<Triple>> entry : sp2Triples.asMap().entrySet()) {
			Collection<Triple> literalTriples = entry.getValue();
			
			if(literalTriples.size() > 1){
				Multimap<String, Triple> map = HashMultimap.create();
				for (Triple t : literalTriples) {
					map.put(t.getObject().getLiteralLexicalForm().replace(".", "").trim(), t);
				}
				
				
				for (Entry<String, Collection<Triple>> entry2 : map.asMap().entrySet()) {
					Collection<Triple> sameObjectDigits = entry2.getValue();
					if(sameObjectDigits.size() > 1){
						Collection<Triple> keep = new HashSet<Triple>();
						for (Triple t : sameObjectDigits) {
							if(t.getObject().getLiteralDatatype() != null){
								keep.add(t);
								break;
							}
						}
						if(keep.isEmpty()){
							keep.add(sameObjectDigits.iterator().next());
						}
						sameObjectDigits.removeAll(keep);
						triples2Remove.addAll(sameObjectDigits);
					}
				}
			}
		}
		triples.removeAll(triples2Remove);
	}
	
	private boolean isNumeric(Node node){
		LiteralLabel literal = node.getLiteral();
		String lexicalForm = literal.getLexicalForm();
		return isNumeric(lexicalForm);
//		try {
//			Integer.parseInt(lexicalForm);
//			return true;
//		} catch (NumberFormatException e) {
//			try {
//				Long.parseLong(lexicalForm);
//				return true;
//			} catch (NumberFormatException e1) {
//				try {
//					Double.parseDouble(lexicalForm);
//					return true;
//				} catch (NumberFormatException e2) {
//					try {
//						Float.parseFloat(lexicalForm);
//						return true;
//					} catch (NumberFormatException e3) {
//						e3.printStackTrace();
//					}
//				}
//			}
//		}
//		return false;
	}
	
	public static boolean isNumeric(String str) {
		return str.matches("-?\\d+(\\.\\d+)?"); 
	}

}
