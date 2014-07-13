/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.naturallanguagegeneration.property;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.jena_sparql_api.model.QueryExecutionFactoryModel;
import org.aksw.sparql2nl.naturallanguagegeneration.Preposition;
import org.aksw.sparql2nl.naturallanguagegeneration.URIConverter;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.google.common.collect.Lists;
import com.hp.hpl.jena.rdf.model.Model;

import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.SynsetType;
import edu.smu.tspell.wordnet.WordNetDatabase;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;

/**
 * Verbalize a property.
 * @author Lorenz Buehmann
 *
 */
public class PropertyVerbalizer {
	
    private static final Logger logger = Logger.getLogger(PropertyVerbalizer.class);
    
    private double threshold = 2.0;
    private Preposition preposition;
    WordNetDatabase database;
    
    private final String VERB_PATTERN = "^((VP)|(have NP)|(be NP P)|(be VP P)|(VP NP)).*";
	private StanfordCoreNLP pipeline;
	private boolean useLinguistics = true;
	
	private final List<String> auxiliaryVerbs = Lists.newArrayList("do", "have", "be", "shall", "can", "may");

	private URIConverter uriConverter;
	
    public PropertyVerbalizer(SparqlEndpoint endpoint, String cacheDirectory, String wordnetDictionary) {
        this(new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs()), wordnetDictionary);
    }
    
    public PropertyVerbalizer(Model model, String wordnetDictionary) {
        this(new QueryExecutionFactoryModel(model), wordnetDictionary);
    }
    
    public PropertyVerbalizer(QueryExecutionFactory qef, String wordnetDictionary) {
        this(new URIConverter(qef), wordnetDictionary);
    }
    
    public PropertyVerbalizer(URIConverter uriConverter, String wordnetDictionary) {
        this.uriConverter = uriConverter;
        
		System.setProperty("wordnet.database.dir", wordnetDictionary);
        database = WordNetDatabase.getFileInstance();
        preposition = new Preposition(this.getClass().getClassLoader().getResourceAsStream("preposition_list.txt"));
        
        Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma, parse");
		props.put("ssplit.isOneSentence","true");
		pipeline = new StanfordCoreNLP(props);
    }
    
    public PropertyVerbalization verbalize(String propertyURI){
    	logger.debug("Getting lexicalization type for \"" + propertyURI + "\"...");
    	
    	//get textual representation for the property URI
    	String propertyText = uriConverter.convert(propertyURI);
    	
    	//normalize the text, e.g. to lower case
    	propertyText = normalize(propertyText);
    	
    	//try to use linguistical information
    	PropertyVerbalizationType verbalizationType = getTypeByLinguisticalAnalysis(propertyText);
    	
    	//if this failed use WordNet heuristic
    	if(verbalizationType == PropertyVerbalizationType.UNSPECIFIED){
    		logger.debug("...using WordNet based analysis...");
    		verbalizationType = getTypeByWordnet(propertyText);
    	}
    	
    	PropertyVerbalization propertyVerbalization = new PropertyVerbalization(propertyURI, propertyText, verbalizationType);
    	
    	//compute expanded form
    	computeExpandedVerbalization(propertyVerbalization);
    	
    	logger.debug("Done.");
    	
    	return propertyVerbalization;
    }
    
    public PropertyVerbalizationType getTypeByWordnet(String property){
    	 //length is > 1
        if (property.contains(" ")) {
            String split[] = property.split(" ");
            String lastToken = split[split.length - 1];
            //first check if the ending is a preposition
            //if yes, then the type is that of the first word
            if (preposition.isPreposition(lastToken)) {
            	String firstToken = split[0];
                if (getTypeByWordnet(firstToken) == PropertyVerbalizationType.NOUN) {
                    return PropertyVerbalizationType.NOUN;
                } else if (getTypeByWordnet(firstToken) == PropertyVerbalizationType.VERB) {
                    return PropertyVerbalizationType.VERB;
                }
            }
            if (getTypeByWordnet(lastToken) == PropertyVerbalizationType.NOUN) {
                return PropertyVerbalizationType.NOUN;
            } else if (getTypeByWordnet(split[0]) == PropertyVerbalizationType.VERB) {
                return PropertyVerbalizationType.VERB;
            } else {
                return PropertyVerbalizationType.NOUN;
            }
        } else {
            double score = getScore(property);
			if (score < 0) {// some count did not work
				return PropertyVerbalizationType.UNSPECIFIED;
			}
			if (score >= threshold) {
				return PropertyVerbalizationType.NOUN;
			} else if (score < 1 / threshold) {
				return PropertyVerbalizationType.VERB;
			} else {
				return PropertyVerbalizationType.NOUN;
			}
        }
    }
    
    public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

    /**
     * Returns log(nounCount/verbCount), i.e., positive for noun, negative for
     * verb
     *
     * @param word Input token
     * @return "Typicity"
     */
    public double getScore(String word) {
        double nounCount = 0;
        double verbCount = 0;
        logger.debug("Checking " + word);
        Synset[] synsets = database.getSynsets(word, SynsetType.NOUN);
        for (int i = 0; i < synsets.length; i++) {
            String[] s = synsets[i].getWordForms();
            for (int j = 0; j < s.length; j++) {//System.out.println(s[j] + ":" + synsets[i].getTagCount(s[j]));
                nounCount = nounCount + Math.log(synsets[i].getTagCount(s[j]) + 1.0);
            }
        }

        synsets = database.getSynsets(word, SynsetType.VERB);
        for (int i = 0; i < synsets.length; i++) {

            String[] s = synsets[i].getWordForms();
            for (int j = 0; j < s.length; j++) {//System.out.println(s[j] + ":" + synsets[i].getTagCount(s[j]));
                verbCount = verbCount + Math.log(synsets[i].getTagCount(s[j]) + 1.0);
            }
        }
//        System.out.println("Noun count = "+nounCount);
//        System.out.println("Verb count = "+verbCount);
//        //verbCount = synsets.length;
        if (verbCount == 0 && nounCount == 0) {
            return 1.0;
        }
        if (verbCount == 0) {
            return Double.MAX_VALUE;
        }
        if (nounCount == 0) {
            return 0.0;
        } else {
            return nounCount / verbCount;
        }
    }

    public ArrayList<String> getAllSynsets(String word) {
        ArrayList<String> synset = new ArrayList<String>();

        WordNetDatabase database = WordNetDatabase.getFileInstance();
        Synset[] synsets = database.getSynsets(word, SynsetType.NOUN, true);
        for (int i = 0; i < synsets.length; i++) {
            synset.add("NOUN " + synsets[i].getWordForms()[0]);
        }
        synsets = database.getSynsets(word, SynsetType.VERB, true);
        for (int i = 0; i < synsets.length; i++) {
            synset.add("VERB " + synsets[i].getWordForms()[0]);
        }

        System.out.println(synset);
        return synset;
    }

    public String getInfinitiveForm(String word) {

        String[] split = word.split(" ");
        String verb = split[0];

        //check for past construction that simply need an auxilliary
        if (verb.endsWith("ed") || verb.endsWith("un") || verb.endsWith("wn") || verb.endsWith("en")) {
            return "be " + word;
        }

        ArrayList<String> synset = new ArrayList<String>();
        WordNetDatabase database = WordNetDatabase.getFileInstance();
        Synset[] synsets = database.getSynsets(verb, SynsetType.VERB, true);
        double min = verb.length();
        String result = verb;
        for (int i = 0; i < synsets.length; i++) {
            String[] wordForms = synsets[i].getWordForms();
            for (int j = 0; j < wordForms.length; j++) {
                if (verb.contains(wordForms[j])) {
                    result = wordForms[j];
                    if (split.length > 1) {
                        for (int k = 1; k < split.length; k++) {
                            result = result + " " + split[k];
                        }
                    }
                    return result;
                }
            }
        }
        return word;
    }
    
	private PropertyVerbalizationType getTypeByLinguisticalAnalysis(String propertyText) {
		logger.debug("...using linguistical analysis...");
		Annotation document = new Annotation(propertyText);
		pipeline.annotate(document);
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);

		String pattern = "";
		for (CoreMap sentence : sentences) {
			List<CoreLabel> tokens = sentence.get(TokensAnnotation.class);
			//get the first word and check if it's 'is' or 'has'
			CoreLabel token = tokens.get(0);
			String word = token.get(TextAnnotation.class);
			String pos = token.get(PartOfSpeechAnnotation.class);
			String lemma = token.getString(LemmaAnnotation.class);
			
			//if first token is an auxiliary can return verb
			if(auxiliaryVerbs.contains(lemma)){
				return PropertyVerbalizationType.VERB;
			}
			
			if(lemma.equals("be") || word.equals("have")){
				pattern += lemma;
			} else {
				if(pos.startsWith("N")){
					pattern += "NP";
				} else if(pos.startsWith("V")){
					pattern += "VP";
				} else {
					pattern += pos;
				}
			}
			if(tokens.size() > 1){
				pattern += " ";
				for (int i = 1; i < tokens.size(); i++) {
					token = tokens.get(i);
					pos = token.get(PartOfSpeechAnnotation.class);
					if(pos.startsWith("N")){
						pattern += "NP";
					} else if(pos.startsWith("V")){
						pattern += "VP";
					} else {
						pattern += pos;
					}
					pattern += " ";
				}
			}
			//get parse tree
			// this is the parse tree of the current sentence
		      Tree tree = sentence.get(TreeAnnotation.class);
		      logger.debug("Parse tree:" + tree.pennString());
		}
		pattern = pattern.trim();
		
		//check if pattern matches
		if(pattern.matches(VERB_PATTERN)){
			logger.debug("...successfully determined type.");
			return PropertyVerbalizationType.VERB;
		} else {
			logger.debug("...could not determine type.");
			return PropertyVerbalizationType.UNSPECIFIED;
		}
	}
	
	private String normalize(String propertyText){
		//lower case
		propertyText = propertyText.toLowerCase();
		
		return propertyText;
	}
	
	private void computeExpandedVerbalization(PropertyVerbalization propertyVerbalization){
		
		String text = propertyVerbalization.getVerbalizationText();
		String expandedForm = text;
		
		//get POS tag of property verbalization
		String pos = getPOS(text);
		
		//VBN IN
		if(pos.equals("VBN IN")){
			expandedForm = "is" + " " + text;
		}
		
		propertyVerbalization.setExpandedVerbalizationText(expandedForm);
	}
	
	private String getPOS(String text){
		Annotation document = new Annotation(text);
		pipeline.annotate(document);
		
		//we assume to have only one sentence
		CoreMap sentence = document.get(SentencesAnnotation.class).get(0);
		
		StringBuilder sb = new StringBuilder();
		for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
			String word = token.get(TextAnnotation.class);
			String pos = token.get(PartOfSpeechAnnotation.class);
			String lemma = token.getString(LemmaAnnotation.class);
			
//			sb.append(word);
//			sb.append('/');
			sb.append(pos);
			sb.append(" ");
		}
		
		return sb.toString().trim();
	}


    public static void main(String args[]) {
        PropertyVerbalizer pp = new PropertyVerbalizer(SparqlEndpoint.getEndpointDBpedia(), "ache", "resources/wordnet/dict");
        
        String propertyURI = "http://dbpedia.org/ontology/birthPlace";
        System.out.println(pp.verbalize(propertyURI));
        
        propertyURI = "http://dbpedia.org/ontology/birthPlace";
        System.out.println(pp.verbalize(propertyURI));
        
        propertyURI = "http://dbpedia.org/ontology/hasColor";
        System.out.println(pp.verbalize(propertyURI));
        
        propertyURI = "http://dbpedia.org/ontology/isHardWorking";
        System.out.println(pp.verbalize(propertyURI));
        
        propertyURI = "http://dbpedia.org/ontology/bornIn";
        System.out.println(pp.verbalize(propertyURI));
        
        propertyURI = "http://dbpedia.org/ontology/cross";
        System.out.println(pp.verbalize(propertyURI));
        
        propertyURI = "http://dbpedia.org/ontology/producedBy";
        System.out.println(pp.verbalize(propertyURI));
        
        propertyURI = "http://dbpedia.org/ontology/worksFor";
        System.out.println(pp.verbalize(propertyURI));
        
        propertyURI = "http://dbpedia.org/ontology/workedFor";
        System.out.println(pp.verbalize(propertyURI));
        
        propertyURI = "http://dbpedia.org/ontology/knownFor";
        System.out.println(pp.verbalize(propertyURI));
        
        propertyURI = "http://dbpedia.org/ontology/name";
        System.out.println(pp.verbalize(propertyURI));
    }
}
