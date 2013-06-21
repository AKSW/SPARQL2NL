/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.clustering.hardening;

/**
 *
 * @author ngonga
 */
public class HardeningFactory {
    public enum HardeningType { LARGEST, SMALLEST, AVERAGE};
    public static Hardening getHardening(HardeningType type)
    {
        if(type.equals(HardeningType.LARGEST)) return new LargestClusterHardening();
        if(type.equals(HardeningType.SMALLEST)) return new SmallestClusterHardening();
        if(type.equals(HardeningType.AVERAGE)) return new AverageWeightClusterHardening();
        else return new LargestClusterHardening();
    }
}
