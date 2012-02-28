/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.smooth_nlg;

/**
 *
 * @author christina
 */
public class Predicate {
    
    String predicate;
    String subject;
    String object;
    
    public Predicate(String s,String p,String o) {
        predicate = p;
        subject = s;
        object = o;
    }
    
    public String toString() {
        return subject + " " + predicate + " " + object;
    }
}
