
package org.aksw.sparql2nl.naturallanguagegeneration;

import simplenlg.phrasespec.SPhraseSpec;

public class Sentence {
    
    SPhraseSpec sps; 
    boolean optional;
    Integer id;
    
    public Sentence(SPhraseSpec s,boolean o,int i) {
        sps = s;
        optional = o;
        id = i;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
    	return sps.toString();
    }
}
