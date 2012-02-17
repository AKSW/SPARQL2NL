/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import com.hp.hpl.jena.graph.query.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import simplenlg.features.Feature;
import simplenlg.framework.*;
import simplenlg.lexicon.*;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.*;

/**
 *
 * @author ngonga
 */
public class GenericNLG {

    Lexicon lexicon;
    NLGFactory nlgFactory;
    Realiser realiser;

    public GenericNLG() {
        lexicon = Lexicon.getDefaultLexicon();
        nlgFactory = new NLGFactory(lexicon);
        realiser = new Realiser(lexicon);
    }

    /** Takes a DBPedia class and returns the correct label for it
     * 
     * @param className Name of a class
     * @return Label
     */
    public NPPhraseSpec getNPPhrase (String className)
    {
        NPPhraseSpec object = nlgFactory.createNounPhrase(className.toLowerCase());
        object.setPlural(true);        
        return object;
    }
    
    public String generateNL(Map<String, Set<String>> typeMap, Query query) {
        
        //first create the beginning of the NLR
        SPhraseSpec p = nlgFactory.createClause();        
            p.setSubject("This query");
            p.setVerb("return");
            List<NPPhraseSpec> objects = new ArrayList<NPPhraseSpec>();
            
        //process the type information to create the object(s)    
        for (String s : typeMap.keySet()) {
            // contains the objects to the sentence
            NPPhraseSpec object = nlgFactory.createNounPhrase(s);            
            Set<String> types = typeMap.get(s);
            for(String type: types)
            {
                object.addPreModifier(getNPPhrase(type));
            }
            object.setFeature(Feature.CONJUNCTION, "or");
            objects.add(object);            
        }
        
        //if only one object go for a simple add, else create a conjunction
        if(objects.size()==1)
        {
            p.setObject(objects.get(0));
        }
        else
        {
            CoordinatedPhraseElement cpe = nlgFactory.createCoordinatedPhrase(objects.get(0), objects.get(1));
            if(objects.size()>2)
                for(int i=2; i<objects.size(); i++)
                    cpe.addCoordinate(objects.get(i));
            p.setObject(cpe);
        }
        
        //now create complement
        
        String output = realiser.realiseSentence(p); // Realiser created earlier.
        System.out.println(output);        
        return output;
    }
    
    public static void main(String args[])
    {
        Map<String, Set<String>> typeMap = new HashMap<String, Set<String>>();
        String s = "x";
        HashSet type = new HashSet();
        type.add("Person");
        //type.add("Village");
        typeMap.put(s, type);
        
        s = "y";
        type = new HashSet();
        type.add("City");
        //type.add("Village");
        typeMap.put(s, type);
        (new GenericNLG()).generateNL(typeMap, null);
    }
}
