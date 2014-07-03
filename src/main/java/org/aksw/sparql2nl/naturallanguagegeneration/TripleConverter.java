/**
 * 
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.sparql2nl.naturallanguagegeneration.PropertyProcessor.Type;
import org.aksw.sparql2nl.nlp.relation.BoaPatternSelector;
import org.aksw.sparql2nl.nlp.stemming.PlingStemmer;
import org.aksw.sparql2nl.queryprocessing.GenericType;
import org.apache.commons.lang.SystemUtils;
import org.apache.log4j.Logger;
import org.dllearner.core.owl.DatatypeProperty;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;

import simplenlg.features.Feature;
import simplenlg.features.InternalFeature;
import simplenlg.features.Tense;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.LexicalCategory;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import com.google.common.collect.Lists;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.NodeFactory;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.graph.impl.LiteralLabel;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

/**
 * Convert triple(s) into natural language.
 * @author Lorenz Buehmann
 * 
 */
public class TripleConverter {
	
	private static final Logger logger = Logger.getLogger(TripleConverter.class.getName());

	private NLGFactory nlgFactory;
	private Realiser realiser;

	private URIConverter uriConverter;
	private LiteralConverter literalConverter;
	private PropertyProcessor pp;
	private SPARQLReasoner reasoner;
	
	private boolean determinePluralForm = false;
	//show language as adjective for literals
	private boolean considerLiteralLanguage = true;
	//encapsulate string literals in quotes ""
	private boolean encapsulateStringLiterals = true;
	//for multiple types use 'as well as' to coordinate the last type
	private boolean useAsWellAsCoordination = true;

	public TripleConverter(SparqlEndpoint endpoint, String cacheDirectory) {
		this(endpoint, cacheDirectory, Lexicon.getDefaultLexicon());
	}
	
	public TripleConverter(QueryExecutionFactory qef, String cacheDirectory) {
		this(qef, cacheDirectory, Lexicon.getDefaultLexicon());
	}
	
	public TripleConverter(SparqlEndpoint endpoint, String cacheDirectory, Lexicon lexicon) {
		nlgFactory = new NLGFactory(lexicon);
		realiser = new Realiser(lexicon);

		uriConverter = new URIConverter(endpoint, cacheDirectory);
		literalConverter = new LiteralConverter(uriConverter);
		literalConverter.setEncapsulateStringLiterals(encapsulateStringLiterals);
		
		String wordnetDirectory;
		if(SystemUtils.IS_OS_WINDOWS){
			wordnetDirectory = this.getClass().getClassLoader().getResource("wordnet/windows/dict").getPath();
		} else {
			wordnetDirectory = this.getClass().getClassLoader().getResource("wordnet/linux/dict").getPath();
		}
		logger.info("WordNet directory: " + wordnetDirectory);
		pp = new PropertyProcessor(wordnetDirectory);
		
		reasoner = new SPARQLReasoner(endpoint, cacheDirectory);
	}
	
	public TripleConverter(QueryExecutionFactory qef, String cacheDirectory, Lexicon lexicon) {
		nlgFactory = new NLGFactory(lexicon);
		realiser = new Realiser(lexicon);

		uriConverter = new URIConverter(qef, cacheDirectory);
		literalConverter = new LiteralConverter(uriConverter);
		literalConverter.setEncapsulateStringLiterals(encapsulateStringLiterals);
		
		String wordnetDirectory;
		if(SystemUtils.IS_OS_WINDOWS){
			wordnetDirectory = this.getClass().getClassLoader().getResource("wordnet/windows/dict").getPath();
		} else {
			wordnetDirectory = this.getClass().getClassLoader().getResource("wordnet/linux/dict").getPath();
		}
		logger.info("WordNet directory: " + wordnetDirectory);
		pp = new PropertyProcessor(wordnetDirectory);
		
		reasoner = new SPARQLReasoner(qef);
	}
	
	public List<SPhraseSpec> convertTriples(Collection<Triple> triples) {
		List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
		for (Triple triple : triples) {
			phrases.add(convertTriple(triple));
		}
		return phrases;
	}
	
	/**
	 * Return a textual representation for the given triples.
	 * Currently we assume that all triples have the same subject!
	 * @param t the triples to convert
	 * @return the textual representation
	 */
	public String convertTriplesToText(Collection<Triple> triples){
		//combine with conjunction
		CoordinatedPhraseElement conjunction = nlgFactory.createCoordinatedPhrase();
		
		//get the type triples first 
		Set<Triple> typeTriples = new HashSet<Triple>();
		Set<Triple> otherTriples = new HashSet<Triple>();
		
		for (Triple triple : triples) {
			if(triple.predicateMatches(RDF.type.asNode())){
				typeTriples.add(triple);
			} else {
				otherTriples.add(triple);
			}
		}
		
		//convert the type triples
		List<SPhraseSpec> typePhrases = convertTriples(typeTriples);
		
		//if there are more than one types, we combine them in a single clause
		if(typePhrases.size() > 1){
			//combine all objects in a coordinated phrase
			CoordinatedPhraseElement combinedObject = nlgFactory.createCoordinatedPhrase();
			
			//the last 2 phrases are combined via 'as well as'
			if(useAsWellAsCoordination){
				SPhraseSpec phrase1 = typePhrases.remove(typePhrases.size() - 1);
				SPhraseSpec phrase2 = typePhrases.get(typePhrases.size() - 1);
				//combine all objects in a coordinated phrase
				CoordinatedPhraseElement combinedLastTwoObjects = nlgFactory.createCoordinatedPhrase(phrase1.getObject(), phrase2.getObject());
				combinedLastTwoObjects.setConjunction("as well as");
				combinedLastTwoObjects.setFeature(Feature.RAISE_SPECIFIER, false);
				combinedLastTwoObjects.setFeature(InternalFeature.SPECIFIER, "a");
				phrase2.setObject(combinedLastTwoObjects);
			}
			
			Iterator<SPhraseSpec> iterator = typePhrases.iterator();
			//pick first phrase as representative
			SPhraseSpec representative = iterator.next();
			combinedObject.addCoordinate(representative.getObject());
			
			while(iterator.hasNext()){
				SPhraseSpec phrase = iterator.next();
				NLGElement object = phrase.getObject();
				combinedObject.addCoordinate(object);
			}
			
			combinedObject.setFeature(Feature.RAISE_SPECIFIER, true);
			//set the coordinated phrase as the object
			representative.setObject(combinedObject);
			//return a single phrase
			typePhrases = Lists.newArrayList(representative);
		}
		for (SPhraseSpec phrase : typePhrases) {
			conjunction.addCoordinate(phrase);
		}
		
		//convert the other triples, but use place holders for the subject
		String placeHolderToken = (typeTriples.isEmpty() || otherTriples.size() == 1) ? "it" : "whose";
		Node placeHolder = NodeFactory.createURI("http://sparql2nl.aksw.org/placeHolder/" + placeHolderToken);
		Collection<Triple> placeHolderTriples = new ArrayList<Triple>(otherTriples.size());
		Iterator<Triple> iterator = otherTriples.iterator();
		//we have to keep one triple with subject if we have no type triples
		if(typeTriples.isEmpty() && iterator.hasNext()){
			placeHolderTriples.add(iterator.next());
		}
		while (iterator.hasNext()) {
			Triple triple = iterator.next();
			Triple newTriple = Triple.create(placeHolder, triple.getPredicate(), triple.getObject());
			placeHolderTriples.add(newTriple);
		}
		
		Collection<SPhraseSpec> otherPhrases = convertTriples(placeHolderTriples);
		
		for (SPhraseSpec phrase : otherPhrases) {
			conjunction.addCoordinate(phrase);
		}
        
		String sentence = realiser.realiseSentence(conjunction);
		return sentence;
	}
	
	/**
	 * Return a textual representation for the given triple.
	 * @param t the triple to convert
	 * @return the textual representation
	 */
	public String convertTripleToText(Triple t){
		return convertTripleToText(t, false);
	}
	
	/**
	 * Return a textual representation for the given triple.
	 * @param t the triple to convert
	 * @param negated if phrase is negated 
	 * @return the textual representation
	 */
	public String convertTripleToText(Triple t, boolean negated){
		NLGElement phrase = convertTriple(t, negated);
		phrase = realiser.realise(phrase);
		return phrase.getRealisation();
	}
	
	/**
	 * Convert a triple into a phrase object
	 * @param t the triple
	 * @return the phrase
	 */
	public SPhraseSpec convertTriple(Triple t) {
		return convertTriple(t, false);
	}
	
	/**
	 * Convert a triple into a phrase object
	 * @param t the triple
	 * @return the phrase
	 */
	public SPhraseSpec convertTriple(Triple t, boolean negated) {
		return convertTriple(t, negated, false);
	}

	/**
	 * Convert a triple into a phrase object
	 * @param t the triple
	 * @param negated if phrase is negated 
	 * @return the phrase
	 */
	public SPhraseSpec convertTriple(Triple t, boolean negated, boolean reverse) {
		SPhraseSpec p = nlgFactory.createClause();

		Node subject = t.getSubject();
		Node predicate = t.getPredicate();
		Node object = t.getObject();

		// process predicate
		// start with variables
		if (predicate.isVariable()) {
			// if subject is variable then use variable label, else generate
			// textual representation
			// first get the string representation for the subject
			NLGElement subjectElement = processSubject(subject);
			p.setSubject(subjectElement);

			// predicate is variable, thus simply use variable label
			p.setVerb("be related via " + predicate.toString() + " to");

			// then process the object
			NLGElement objectElement = processObject(object, false);
			p.setObject(objectElement);
		} // more interesting case. Predicate is not a variable
			// then check for noun and verb. If the predicate is a noun or a
			// verb, then
			// use possessive or verbal form, else simply get the boa pattern
		else {
			//check if object is class
			boolean objectIsClass = predicate.matches(RDF.type.asNode());
			
			// first get the string representation for the subject
			NLGElement subjectElement = processSubject(subject);

			// then process the object
			NPPhraseSpec objectElement = processObject(object, objectIsClass);

			// handle the predicate
			String predicateAsString = uriConverter.convert(predicate.toString());
			predicateAsString = predicateAsString.toLowerCase();
			if (predicateAsString.contains("(")) {
				predicateAsString = predicateAsString.substring(0, predicateAsString.indexOf("("));
			}

			// if the object is a class we generate 'SUBJECT be a OBJECT'
			if (objectIsClass) {
				p.setSubject(subjectElement);
				p.setVerb("be");
				objectElement.setSpecifier("a");
				p.setObject(objectElement);
			} else {
				// get the lexicalization type of the predicate
				Type type;
				if (predicate.matches(RDFS.label.asNode())) {
					type = Type.NOUN;
				} else {
					type = pp.getType(predicateAsString);
				}

				/*-
				 * if the predicate is a noun we generate a possessive form, i.e. 'SUBJECT'S PREDICATE be OBJECT'
				 */
				if (type == Type.NOUN) {
					//subject is a noun with possessive feature
					subjectElement = nlgFactory.createWord(realiser.realise(subjectElement).getRealisation(),
							LexicalCategory.NOUN);
					subjectElement.setFeature(Feature.POSSESSIVE, true);
					//convert predicate into noun
					NLGElement nnp = nlgFactory.createInflectedWord(PlingStemmer.stem(predicateAsString),
							LexicalCategory.NOUN);
					//build noun phrase
					NPPhraseSpec predicateNounPhrase = nlgFactory.createNounPhrase(nnp);
					//check if object is a string literal with a language tag
					if(considerLiteralLanguage){
						if(object.isLiteral() && object.getLiteralLanguage() != null){
							String languageTag = object.getLiteralLanguage();
							String language = Locale.forLanguageTag(languageTag).getDisplayLanguage();
							predicateNounPhrase.setPreModifier(language);
						}
					}
					//build noun phrase
					NPPhraseSpec nounPhrase = nlgFactory.createNounPhrase();
					nounPhrase.setHead(predicateNounPhrase);
					
					//set subject as pre modifier
					nounPhrase.setPreModifier(subjectElement);
					p.setSubject(nounPhrase);
					
					// we use 'be' as the new predicate
					p.setVerb("be");
					
					//add object
					p.setObject(objectElement);
					
					//check if we have to use the plural form
					//simple heuristic: OBJECT is variable and predicate is of type owl:FunctionalProperty or rdfs:range is xsd:boolean
					boolean isPlural = determinePluralForm && usePluralForm(t);
					nounPhrase.setPlural(isPlural);
					p.setPlural(isPlural);
					
					//check if we reverse the triple representation
					if(reverse){
						subjectElement.setFeature(Feature.POSSESSIVE, false);
			        	p.setSubject(subjectElement);
			        	p.setVerbPhrase(nlgFactory.createVerbPhrase("be " + predicateAsString + " of"));
			        	p.setObject(objectElement);
					}
				}// if the predicate is a verb 
				else if (type == Type.VERB) { 
					p.setSubject(subjectElement);
					p.setVerb(pp.getInfinitiveForm(predicateAsString));
					p.setObject(objectElement);
				}// in other cases, use the BOA pattern
				else {

					List<org.aksw.sparql2nl.nlp.relation.Pattern> l = BoaPatternSelector
							.getNaturalLanguageRepresentation(predicate.toString(), 1);
					if (l.size() > 0) {
						String boaPattern = l.get(0).naturalLanguageRepresentation;
						// range before domain
						if (boaPattern.startsWith("?R?")) {
							p.setSubject(subjectElement);
							p.setObject(objectElement);
						} else {
							p.setObject(subjectElement);
							p.setSubject(objectElement);
						}
						p.setVerb(BoaPatternSelector.getNaturalLanguageRepresentation(predicate.toString(), 1)
								.get(0).naturalLanguageRepresentationWithoutVariables);
					} // last resort, i.e., no BOA pattern found
					else {
						p.setSubject(subjectElement);
						p.setVerb("be related via \"" + predicateAsString + "\" to");
						p.setObject(objectElement);
					}
				}
			}
		}
		//check if the meaning of the triple is it's negation, which holds for boolean properties with FALSE as value
		if(!negated){
			//check if object is boolean literal
			if(object.isLiteral() && object.getLiteralDatatype() != null && object.getLiteralDatatype().equals(XSDDatatype.XSDboolean)){
				//omit the object
				p.setObject(null);
				
				negated = !(boolean) object.getLiteralValue();
				
			}
		}
		
		//set negation
		if(negated){
			p.setFeature(Feature.NEGATED, negated);
		}
		
		//set present time as tense
		p.setFeature(Feature.TENSE, Tense.PRESENT);

		return p;
	}
	
	/**
	 * @param encapsulateStringLiterals the encapsulateStringLiterals to set
	 */
	public void setEncapsulateStringLiterals(boolean encapsulateStringLiterals) {
		this.literalConverter.setEncapsulateStringLiterals(encapsulateStringLiterals);
	}
	
	/**
	 * @param determinePluralForm the determinePluralForm to set
	 */
	public void setDeterminePluralForm(boolean determinePluralForm) {
		this.determinePluralForm = determinePluralForm;
	}
	
	/**
	 * @param considerLiteralLanguage the considerLiteralLanguage to set
	 */
	public void setConsiderLiteralLanguage(boolean considerLiteralLanguage) {
		this.considerLiteralLanguage = considerLiteralLanguage;
	}
	
	private boolean usePluralForm(Triple triple){
		return triple.getObject().isVariable() 
				&& !(reasoner.isFunctional(new ObjectProperty(triple.getPredicate().getURI())) 
					|| reasoner.getRange(new DatatypeProperty(triple.getPredicate().getURI())).toString().equals(XSDDatatype.XSDboolean.getURI()));
	}

	private NLGElement processSubject(Node subject) {
		NLGElement element;
		if (subject.isVariable()) {
			element = nlgFactory.createWord(subject.toString(), LexicalCategory.NOUN);
		} else {
			element = nlgFactory.createWord(uriConverter.convert(subject.toString()), LexicalCategory.NOUN);
		}
		return element;
	}

	private NPPhraseSpec processObject(Node object, boolean isClass) {
		NPPhraseSpec element;
		if (object.isVariable()) {
			element = nlgFactory.createNounPhrase(object.toString());
		} else if (object.isLiteral()) {
			LiteralLabel lit = object.getLiteral();
			// convert the literal
			String literalText = literalConverter.convert(lit);
			NPPhraseSpec np = nlgFactory.createNounPhrase(nlgFactory.createInflectedWord(literalText,
					LexicalCategory.NOUN));
			np.setPlural(literalConverter.isPlural(lit));
			element = np;
		} else if (object.isURI()) {
			element = getNPPhrase(object.toString(), false, isClass);
		} else {
			throw new IllegalArgumentException("Can not convert blank node " + object + ".");
		}
		return element;
	}

	/**
	 * Takes a URI and returns a noun phrase for it
	 * 
	 * @param uri
	 *            the URI to convert
	 * @param plural
	 *            whether is it is in plural form
	 * @param isClass
	 *            if URI is supposed to be a class
	 * @return
	 */
	public NPPhraseSpec getNPPhrase(String uri, boolean plural, boolean isClass) {
		NPPhraseSpec object = null;
		if (uri.equals(OWL.Thing.getURI())) {
			object = nlgFactory.createNounPhrase(GenericType.ENTITY.getNlr());
		} else if (uri.equals(RDFS.Literal.getURI())) {
			object = nlgFactory.createNounPhrase(GenericType.VALUE.getNlr());
		} else if (uri.equals(RDF.Property.getURI())) {
			object = nlgFactory.createNounPhrase(GenericType.RELATION.getNlr());
		} else if (uri.equals(RDF.type.getURI())) {
			object = nlgFactory.createNounPhrase(GenericType.TYPE.getNlr());
		} else {
			String label = uriConverter.convert(uri);
			if (label != null) {
				if (isClass) {
					//get the singular form
					label = PlingStemmer.stem(label);
					//we assume that classes are always used in lower case format
					label = label.toLowerCase();
				}
				object = nlgFactory.createNounPhrase(nlgFactory.createInflectedWord(label, LexicalCategory.NOUN));
			} else {
				object = nlgFactory.createNounPhrase(GenericType.ENTITY.getNlr());
			}

		}
		object.setPlural(plural);

		return object;
	}
	
	public static void main(String[] args) throws Exception {
		TripleConverter converter = new TripleConverter(SparqlEndpoint.getEndpointDBpedia(), "cache");
		Triple t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Leipzig"),
				NodeFactory.createURI("http://dbpedia.org/ontology/leaderParty"),
				NodeFactory.createURI("http://dbpedia.org/resource/Social_Democratic_Party_of_Germany"));
		String text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Brad_Pitt"),
				NodeFactory.createURI("http://dbpedia.org/ontology/isBornIn"),
				NodeFactory.createURI("http://dbpedia.org/resource/Shawnee,_Oklahoma"));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Brad_Pitt"),
				RDF.type.asNode(),
				NodeFactory.createURI("http://dbpedia.org/ontology/OldActor"));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Ferrari"),
				NodeFactory.createURI("http://dbpedia.org/ontology/hasColor"),
				NodeFactory.createURI("http://dbpedia.org/resource/red"));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/John"),
				NodeFactory.createURI("http://dbpedia.org/ontology/likes"),
				NodeFactory.createURI("http://dbpedia.org/resource/Mary"));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Mount_Everest"),
				NodeFactory.createURI("http://dbpedia.org/ontology/height"),
				NodeFactory.createLiteral("8000", XSDDatatype.XSDinteger));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/page/Mathematics_of_Computation"),
				NodeFactory.createURI("http://dbpedia.org/ontology/isPeerReviewed"),
				NodeFactory.createLiteral("true", XSDDatatype.XSDboolean));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Living_Bird"),
				NodeFactory.createURI("http://dbpedia.org/ontology/isPeerReviewed"),
				NodeFactory.createLiteral("false", XSDDatatype.XSDboolean));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Usain_Bolt"),
				NodeFactory.createURI("http://dbpedia.org/ontology/isGoldMedalWinner"),
				NodeFactory.createLiteral("false", XSDDatatype.XSDboolean));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
					NodeFactory.createURI("http://dbpedia.org/resource/Albert_Einstein"),
					NodeFactory.createURI("http://dbpedia.org/ontology/birthPlace"),
					NodeFactory.createURI("http://dbpedia.org/resource/Ulm"));
		text = converter.convertTripleToText(t, false);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Mount_Everest"),
				NodeFactory.createURI("http://dbpedia.org/ontology/isLargerThan"),
				NodeFactory.createURI("http://dbpedia.org/resource/K2"));
	text = converter.convertTripleToText(t, false);
	System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Albert_Einstein"),
				NodeFactory.createURI("http://dbpedia.org/ontology/birthDate"),
				NodeFactory.createLiteral("1879-03-14", XSDDatatype.XSDdate));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Albert_Einstein"),
				NodeFactory.createURI("http://dbpedia.org/ontology/birthDate"),
				NodeFactory.createVariable("date"));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Lionel_Messi"),
				NodeFactory.createURI("http://dbpedia.org/ontology/team"),
				NodeFactory.createVariable("team"));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		converter.setDeterminePluralForm(true);
		text = converter.convertTripleToText(t);
		converter.setDeterminePluralForm(false);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Living_Bird_III"),
				NodeFactory.createURI("http://dbpedia.org/ontology/isPeerReviewed"),
				NodeFactory.createVariable("isReviewed"));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		t = Triple.create(
				NodeFactory.createURI("http://dbpedia.org/resource/Lionel_Messi"),
				RDFS.label.asNode(),
				NodeFactory.createLiteral("Lionel Messi", "en", null));
		text = converter.convertTripleToText(t);
		System.out.println(t + " -> " + text);
		
		//check conversion of set of triples for the same subject
		Collection<Triple> triples = new ArrayList<Triple>();
		Node subject = NodeFactory.createURI("http://dbpedia.org/resource/Albert_Einstein");
		triples.add(Triple.create(
				subject,
				RDF.type.asNode(),
				NodeFactory.createURI("http://dbpedia.org/ontology/Person")));
		triples.add(Triple.create(
				subject,
				NodeFactory.createURI("http://dbpedia.org/ontology/birthDate"),
				NodeFactory.createLiteral("1879-03-14", XSDDatatype.XSDdate)));
		triples.add(Triple.create(
				subject,
				NodeFactory.createURI("http://dbpedia.org/ontology/birthPlace"),
				NodeFactory.createURI("http://dbpedia.org/resource/Ulm")));
		
		text = converter.convertTriplesToText(triples);
		System.out.println(triples + "\n-> " + text);
		
		triples = new ArrayList<Triple>();
		triples.add(Triple.create(
				subject,
				RDF.type.asNode(),
				NodeFactory.createURI("http://dbpedia.org/ontology/Person")));
		triples.add(Triple.create(
				subject,
				NodeFactory.createURI("http://dbpedia.org/ontology/birthDate"),
				NodeFactory.createLiteral("1879-03-14", XSDDatatype.XSDdate)));
		
		//2 types
		triples = new ArrayList<Triple>();
		triples.add(Triple.create(
				subject,
				RDF.type.asNode(),
				NodeFactory.createURI("http://dbpedia.org/ontology/Physican")));
		triples.add(Triple.create(
				subject,
				RDF.type.asNode(),
				NodeFactory.createURI("http://dbpedia.org/ontology/Musican")));
		triples.add(Triple.create(
				subject,
				NodeFactory.createURI("http://dbpedia.org/ontology/birthDate"),
				NodeFactory.createLiteral("1879-03-14", XSDDatatype.XSDdate)));
		
		text = converter.convertTriplesToText(triples);
		System.out.println(triples + "\n-> " + text);
		
		//more than 2 types
		triples = new ArrayList<Triple>();
		triples.add(Triple.create(
				subject,
				RDF.type.asNode(),
				NodeFactory.createURI("http://dbpedia.org/ontology/Physican")));
		triples.add(Triple.create(
				subject,
				RDF.type.asNode(),
				NodeFactory.createURI("http://dbpedia.org/ontology/Musican")));
		triples.add(Triple.create(
				subject,
				RDF.type.asNode(),
				NodeFactory.createURI("http://dbpedia.org/ontology/Philosopher")));
		triples.add(Triple.create(
				subject,
				NodeFactory.createURI("http://dbpedia.org/ontology/birthDate"),
				NodeFactory.createLiteral("1879-03-14", XSDDatatype.XSDdate)));
		
		text = converter.convertTriplesToText(triples);
		System.out.println(triples + "\n-> " + text);
		
		//no type
		triples = new ArrayList<Triple>();
		triples.add(Triple.create(
				subject,
				NodeFactory.createURI("http://dbpedia.org/ontology/birthPlace"),
				NodeFactory.createURI("http://dbpedia.org/resource/Ulm")));
		triples.add(Triple.create(
				subject,
				NodeFactory.createURI("http://dbpedia.org/ontology/birthDate"),
				NodeFactory.createLiteral("1879-03-14", XSDDatatype.XSDdate)));
		
		text = converter.convertTriplesToText(triples);
		System.out.println(triples + "\n-> " + text);
		
		
	}

}
