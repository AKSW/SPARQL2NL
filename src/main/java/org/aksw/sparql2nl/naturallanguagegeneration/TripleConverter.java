/**
 * 
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.aksw.jena_sparql_api.cache.core.QueryExecutionFactoryCacheEx;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.sparql2nl.naturallanguagegeneration.property.NounPredicateConversion;
import org.aksw.sparql2nl.naturallanguagegeneration.property.PropertyVerbalization;
import org.aksw.sparql2nl.naturallanguagegeneration.property.PropertyVerbalizationType;
import org.aksw.sparql2nl.naturallanguagegeneration.property.PropertyVerbalizer;
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
import simplenlg.features.LexicalFeature;
import simplenlg.features.Tense;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.LexicalCategory;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.PPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.phrasespec.VPPhraseSpec;
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
	private PropertyVerbalizer pp;
	private SPARQLReasoner reasoner;
	
	private boolean determinePluralForm = false;
	//show language as adjective for literals
	private boolean considerLiteralLanguage = true;
	//encapsulate string literals in quotes ""
	private boolean encapsulateStringLiterals = true;
	//for multiple types use 'as well as' to coordinate the last type
	private boolean useAsWellAsCoordination = true;

	private boolean useCompactOfVerbalization = true;
	
	public TripleConverter(SparqlEndpoint endpoint, String cacheDirectory, String wordnetDirectory, Lexicon lexicon) {
		this(new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs()), null,
				null, cacheDirectory, wordnetDirectory, lexicon);
	}
	
	public TripleConverter(SparqlEndpoint endpoint, String cacheDirectory, String wordnetDirectory) {
		this(new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs()), 
				null, null, cacheDirectory, wordnetDirectory, Lexicon.getDefaultLexicon());
	}

	public TripleConverter(QueryExecutionFactory qef, URIConverter uriConverter, String cacheDirectory, String wordnetDirectory) {
		this(qef, null, uriConverter, cacheDirectory, wordnetDirectory, Lexicon.getDefaultLexicon());
	}
	
	public TripleConverter(QueryExecutionFactory qef, String cacheDirectory, Lexicon lexicon) {
		this(qef, null, null, cacheDirectory, null, lexicon);
	}
	
	public TripleConverter(QueryExecutionFactory qef, PropertyVerbalizer propertyVerbalizer, URIConverter uriConverter, String cacheDirectory, String wordnetDirectory, Lexicon lexicon) {
		if(uriConverter == null){
			uriConverter = new URIConverter(qef, cacheDirectory);
		}
		this.uriConverter = uriConverter;
		
		if(wordnetDirectory == null){
			if(SystemUtils.IS_OS_WINDOWS){
				wordnetDirectory = this.getClass().getClassLoader().getResource("wordnet/windows/dict").getPath();
			} else {
				wordnetDirectory = this.getClass().getClassLoader().getResource("wordnet/linux/dict").getPath();
			}
		}
		
		if(propertyVerbalizer == null){
			propertyVerbalizer = new PropertyVerbalizer(uriConverter, wordnetDirectory);
		}
		pp = propertyVerbalizer;
		
		nlgFactory = new NLGFactory(lexicon);
		realiser = new Realiser(lexicon);
		
		literalConverter = new LiteralConverter(uriConverter);
		literalConverter.setEncapsulateStringLiterals(encapsulateStringLiterals);
		
		logger.info("WordNet directory: " + wordnetDirectory);
		
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
		//we have to use whose because the possessive form of who is who's
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
		logger.debug("Verbalizing triple " + t);
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
			PropertyVerbalization propertyVerbalization = pp.verbalize(predicate.getURI());
			String predicateAsString = propertyVerbalization.getVerbalizationText();
			
			// if the object is a class we generate 'SUBJECT be a OBJECT'
			if (objectIsClass) {
				p.setSubject(subjectElement);
				p.setVerb("be");
				objectElement.setSpecifier("a");
				p.setObject(objectElement);
			} else {
				// get the lexicalization type of the predicate
				
				PropertyVerbalizationType type;
				if (predicate.matches(RDFS.label.asNode())) {
					type = PropertyVerbalizationType.NOUN;
				} else {
					type = propertyVerbalization.getVerbalizationType();
				}

				/*-
				 * if the predicate is a noun we generate a possessive form, i.e. 'SUBJECT'S PREDICATE be OBJECT'
				 */
				if (type == PropertyVerbalizationType.NOUN) {
					//subject is a noun with possessive feature
					NLGElement subjectWord = nlgFactory.createInflectedWord(realiser.realise(subjectElement).getRealisation(), LexicalCategory.NOUN);
					subjectWord.setFeature(LexicalFeature.PROPER, true);
					subjectElement = nlgFactory.createNounPhrase(subjectWord);
					subjectElement.setFeature(Feature.POSSESSIVE, true);
	                //build the noun phrase for the predicate
	                NPPhraseSpec predicateNounPhrase = nlgFactory.createNounPhrase(PlingStemmer.stem(predicateAsString));
	                //set the possessive subject as specifier
	                predicateNounPhrase.setFeature(InternalFeature.SPECIFIER, subjectElement);
					
					//check if object is a string literal with a language tag
					if(considerLiteralLanguage){
						if(object.isLiteral() && object.getLiteralLanguage() != null && !object.getLiteralLanguage().isEmpty()){
							String languageTag = object.getLiteralLanguage();
							String language = Locale.forLanguageTag(languageTag).getDisplayLanguage();
							predicateNounPhrase.setPreModifier(language);
						}
					}
					
					p.setSubject(predicateNounPhrase);
					
					// we use 'be' as the new predicate
					p.setVerb("be");
					
					//add object
					p.setObject(objectElement);
					
					//check if we have to use the plural form
					//simple heuristic: OBJECT is variable and predicate is of type owl:FunctionalProperty or rdfs:range is xsd:boolean
					boolean isPlural = determinePluralForm && usePluralForm(t);
					predicateNounPhrase.setPlural(isPlural);
					p.setPlural(isPlural);
					
					//check if we reverse the triple representation
					if(reverse){
						subjectElement.setFeature(Feature.POSSESSIVE, false);
			        	p.setSubject(subjectElement);
			        	p.setVerbPhrase(nlgFactory.createVerbPhrase("be " + predicateAsString + " of"));
			        	p.setObject(objectElement);
					}
				}// if the predicate is a verb 
				else if (type == PropertyVerbalizationType.VERB) { 
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
//		System.out.println(realiser.realise(p));
		return p;
	}
	
	public NPPhraseSpec convertTriplePattern(Triple t, NLGElement subjectElement, NLGElement objectElement, boolean plural, boolean negated, boolean reverse) {
		return convertTriplePattern(t, subjectElement, objectElement, plural, negated, reverse, NounPredicateConversion.RELATIVE_CLAUSE_COMPLEMENTIZER);
	}
	
	/**
	 * Convert a triple pattern into "v(PREDICATE)s of v(OBJECT)"
	 * @param t the triple
	 * @param negated if phrase is negated 
	 * @return the phrase
	 */
	public NPPhraseSpec convertTriplePatternCompactOfForm(Triple t, NLGElement objectElement) {
		// handle the predicate
		PropertyVerbalization propertyVerbalization = pp.verbalize(t.getPredicate().getURI());
		String predicateAsString = propertyVerbalization.getVerbalizationText();

		NPPhraseSpec np = nlgFactory.createNounPhrase(predicateAsString);
		PPPhraseSpec pp = nlgFactory.createPrepositionPhrase("of", objectElement);
		np.addComplement(pp);
		return np;

	}
	
	
	/**
	 * Convert a triple into a phrase object
	 * @param t the triple
	 * @param negated if phrase is negated 
	 * @return the phrase
	 */
	public NPPhraseSpec convertTriplePattern(Triple t, NLGElement subjectElement, NLGElement objectElement, boolean plural, boolean negated, boolean reverse, NounPredicateConversion nounPredicateConversion) {
		NPPhraseSpec np = nlgFactory.createNounPhrase(realiser.realise(subjectElement).getRealisation());
		SPhraseSpec clause = null;
		
		Node subject = t.getSubject();
		Node predicate = t.getPredicate();
		Node object = t.getObject();
		
		//if there is no object element we convert it into the existence of the triple pattern
		if(objectElement == null){
//			objectElement = processObject(object, false);
		}

		// process predicate
		// start with variables
		if (predicate.isVariable()) {
			clause = nlgFactory.createClause(null, "be related via " + predicate.toString() + " to", objectElement);
		} // more interesting case. Predicate is not a variable
			// then check for noun and verb. If the predicate is a noun or a
			// verb, then
			// use possessive or verbal form, else simply get the boa pattern
		else {
			// handle the predicate
			PropertyVerbalization propertyVerbalization = pp.verbalize(predicate.getURI());
			String predicateAsString = propertyVerbalization.getVerbalizationText();

			// get the lexicalization type of the predicate
			PropertyVerbalizationType type;
			if (predicate.matches(RDFS.label.asNode())) {
				type = PropertyVerbalizationType.NOUN;
			} else {
				type = propertyVerbalization.getVerbalizationType();
			}

			/*-
			 * if the predicate is a noun we generate a possessive form, i.e. 'SUBJECT'S PREDICATE be OBJECT'
			 */
			clause = nlgFactory.createClause();
			np.addComplement(clause);
			if (type == PropertyVerbalizationType.NOUN) {
				//reversed triple pattern -> SUBJECT that be NP of OBJECT
				if(reverse){
					VPPhraseSpec vp = nlgFactory.createVerbPhrase("be");
					vp.setIndirectObject(nlgFactory.createNounPhrase(predicateAsString));
					clause.setVerbPhrase(vp);
					PPPhraseSpec pp = nlgFactory.createPrepositionPhrase("of", objectElement);
					clause.setObject(pp);
				} else {
					if(objectElement == null){
						VPPhraseSpec vp = nlgFactory.createVerbPhrase("have");
						vp.setIndirectObject(nlgFactory.createNounPhrase("a", predicateAsString));
						clause.setVerbPhrase(vp);
						clause.setObject(objectElement);
					} else {
						//if predicate ends with "of" -> SUBJECT be PREDICATE OBJECT
						if(predicateAsString.endsWith(" of")){
							if(useCompactOfVerbalization){
								predicateAsString = predicateAsString.replaceFirst("( of)$", "");
								np.setNoun(predicateAsString);
								PPPhraseSpec pp = nlgFactory.createPrepositionPhrase("of", objectElement);
								np.clearComplements();
								np.setComplement(pp);
							} else {
								VPPhraseSpec vp = nlgFactory.createVerbPhrase("be");
								// vp.setFeature(Feature.PROGRESSIVE, true);
								vp.setIndirectObject(nlgFactory.createNounPhrase(predicateAsString));
								clause.setVerbPhrase(vp);
								clause.setObject(objectElement);
							}
						} else {
							switch (nounPredicateConversion) {
							case RELATIVE_CLAUSE_COMPLEMENTIZER: {
								VPPhraseSpec vp = nlgFactory.createVerbPhrase("have");
								vp.setIndirectObject(nlgFactory.createNounPhrase(predicateAsString));
								clause.setVerbPhrase(vp);
								clause.setObject(objectElement);
							}
								break;
							case RELATIVE_CLAUSE_PRONOUN: {
								clause.setSubject(predicateAsString);
								clause.setVerb("be");
								clause.setObject(objectElement);
								NLGElement complementiser = nlgFactory.createInflectedWord("whose", LexicalCategory.PRONOUN);
								complementiser.setFeature(InternalFeature.NON_MORPH, true);
								complementiser.setFeature(Feature.POSSESSIVE, true);
								clause.setFeature(Feature.COMPLEMENTISER, complementiser);
							}
								break;
							case REDUCED_RELATIVE_CLAUSE: {
								VPPhraseSpec vp = nlgFactory.createVerbPhrase("having");
								// vp.setFeature(Feature.PROGRESSIVE, true);
								vp.setIndirectObject(nlgFactory.createNounPhrase(predicateAsString));
								clause.setVerbPhrase(vp);
								clause.setObject(objectElement);
							}
								break;
							default:
								;
							}
						}
					}
				}
			}// if the predicate is a verb
			else if (type == PropertyVerbalizationType.VERB) {
				clause.setVerb(predicateAsString);
				clause.setObject(objectElement);
			}
		}
		System.out.println(realiser.realise(np));
		
		//check if the meaning of the triple is it's negation, which holds for boolean properties with FALSE as value
		if(!negated){
			//check if object is boolean literal
			if(object.isLiteral() && object.getLiteralDatatype() != null && object.getLiteralDatatype().equals(XSDDatatype.XSDboolean)){
				//omit the object
				clause.setObject(null);
				
				negated = !(boolean) object.getLiteralValue();
				
			}
		}
		
		//set negation
		if(negated){
			clause.setFeature(Feature.NEGATED, negated);
		}
		
		//set present time as tense
		clause.setFeature(Feature.TENSE, Tense.PRESENT);
//		clause.setPlural(plural);
		np.setPlural(plural);
		clause.setPlural(plural);

		return np;
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
	
	public NLGElement processNode(Node node) {
		NLGElement element;
		if (node.isVariable()) {
			element = processVarNode(node);
		} else if(node.isURI()){
			element = nlgFactory.createNounPhrase(nlgFactory.createWord(uriConverter.convert(node.getURI()), LexicalCategory.NOUN));
		} else if(node.isLiteral()){
			element = processLiteralNode(node);
		} else {
			throw new UnsupportedOperationException("Can not convert blank node.");
		}
		return element;
	}
	
	public NPPhraseSpec processClassNode(Node node, boolean plural) {
		NPPhraseSpec object = null;
		if (node.equals(OWL.Thing.asNode())) {
			object = nlgFactory.createNounPhrase(GenericType.ENTITY.getNlr());
		} else if (node.equals(RDFS.Literal.getURI())) {
			object = nlgFactory.createNounPhrase(GenericType.VALUE.getNlr());
		} else if (node.equals(RDF.Property.getURI())) {
			object = nlgFactory.createNounPhrase(GenericType.RELATION.getNlr());
		} else if (node.equals(RDF.type.getURI())) {
			object = nlgFactory.createNounPhrase(GenericType.TYPE.getNlr());
		} else {
			String label = uriConverter.convert(node.getURI());
			if (label != null) {
				//get the singular form
				label = PlingStemmer.stem(label);
				//we assume that classes are always used in lower case format
				label = label.toLowerCase();
				object = nlgFactory.createNounPhrase(nlgFactory.createInflectedWord(label, LexicalCategory.NOUN));
			} else {
				object = nlgFactory.createNounPhrase(GenericType.ENTITY.getNlr());
			}

		}
		//set plural form
		object.setPlural(plural);
		return object;
	}
	
	public NPPhraseSpec processVarNode(Node varNode) {
		return nlgFactory.createNounPhrase(varNode.toString());
	}
	
	public NPPhraseSpec processLiteralNode(Node node) {
		LiteralLabel lit = node.getLiteral();
		// convert the literal
		String literalText = literalConverter.convert(lit);
		NPPhraseSpec np = nlgFactory.createNounPhrase(nlgFactory.createInflectedWord(literalText,
				LexicalCategory.NOUN));
		np.setPlural(literalConverter.isPlural(lit));
		return np;
	}

	private NLGElement processSubject(Node subject) {
		NLGElement element;
		if (subject.isVariable()) {
			element = nlgFactory.createNounPhrase(subject.toString());
		} else {
			element = nlgFactory.createNounPhrase(uriConverter.convert(subject.getURI()));
		}
		return element;
	}

	private NPPhraseSpec processObject(Node object, boolean isClass) {
		NPPhraseSpec element;
		if (object.isVariable()) {
			element = processVarNode(object);
		} else if (object.isLiteral()) {
			element = processLiteralNode(object);
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
		TripleConverter converter = new TripleConverter(SparqlEndpoint.getEndpointDBpedia(), "cache", null);
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
