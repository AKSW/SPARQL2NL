/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.clustering;

import org.dllearner.core.owl.Property;
/**
 *
 * @author ngonga
 */
public class PropertyNode extends Node {
    public Property property;
    
    public PropertyNode(Property p)
    {
        super(p.getName());
//        this.label = p.getName();
        this.property = p;
    }
}
