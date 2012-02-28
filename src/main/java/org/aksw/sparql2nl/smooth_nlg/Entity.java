/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.smooth_nlg;

import java.util.Set;

/**
 *
 * @author christina
 */
public class Entity {
    
    String var;
    boolean count;
    String type;
    Set<Predicate> properties;
    
    public Entity(String v,boolean c,String t,Set<Predicate> ps) {
        var = v;
        count = c;
        type = t;
        properties = ps;
    }
    
    public String toString() {
        String out = "";
        if (count) { out += " number of "; } 
        out += var + " (" + type + ") : {";
        for (Predicate p : properties) {
            out += p.toString() + ", ";
        }
        out = out.substring(0,out.length()-2);
        out += "}";
        return out;
    }
}
