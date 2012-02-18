/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import com.hp.hpl.jena.query.Query;
import simplenlg.framework.DocumentElement;

/**
 * Interface for tools that transform SPARQL queries into natural language
 * @author ngonga
 */
public interface Sparql2NLConverter {
    
    /** Takes a query as input and returns a natural language representation of 
     * the input.
     * @param query Input query
     * @return Natural language representation as strings
     */
    public String getNLR(Query query);
    /** Converts a SPARQL query into a natural language element that can be processed 
     * further.
     * @param query Input query
     * @return Natural Language Element
     */
    public DocumentElement convert2NLE(Query query);
}
