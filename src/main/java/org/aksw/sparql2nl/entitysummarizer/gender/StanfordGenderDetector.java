/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer.gender;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.GenderAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.util.CoreMap;

/**
 * @author Lorenz Buehmann
 *
 */
public class StanfordGenderDetector implements GenderDetector{

	/**
	 * 
	 */
	public StanfordGenderDetector() {
	
	}
		

	/* (non-Javadoc)
	 * @see org.aksw.sparql2nl.entitysummarizer.gender.GenderDetector#getGender(java.lang.String)
	 */
	@Override
	public Gender getGender(String text) {
		Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit, pos, lemma, ner, gender");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
     // create an empty Annotation just with the given text
        Annotation document = new Annotation(text);
        
        // run all Annotators on this text
        pipeline.annotate(document);
        
        // these are all the sentences in this document
        // a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        
        for(CoreMap sentence: sentences) {
          // traversing the words in the current sentence
          // a CoreLabel is a CoreMap with additional token-specific methods
          for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
            // this is the text of the token
            String word = token.get(TextAnnotation.class);
            // this is the POS tag of the token
            String pos = token.get(PartOfSpeechAnnotation.class);
            // this is the NER label of the token
            String ne = token.get(NamedEntityTagAnnotation.class);  
            
            String gender = token.get(GenderAnnotation.class);
            System.out.println(gender);
          }
        }
		return null;
	}
	
	public static void main(String[] args) throws Exception {
		String s = "Mike's mother likes apples.";
		StanfordGenderDetector genderDetector = new StanfordGenderDetector();
		System.out.println(genderDetector.getGender(s));
	}

}
