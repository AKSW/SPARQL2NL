package org.aksw.sparql2nl.naturallanguagegeneration;

import java.io.File;
import java.io.InputStream;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataPropertyAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLPropertyAxiom;

public class StatisticalFunctionalityDetector implements FunctionalityDetector{
	
	private OWLOntology ontology;
	private double threshold;
	private OWLDataFactory dataFactory;
	
	private final IRI confidencePropertyIRI = IRI.create("http://www.dl-learner.org/ontologies/enrichment.owl#confidence");
	
	public StatisticalFunctionalityDetector(File ontologyFile, double threshold) {
		try {
			OWLOntologyManager man = OWLManager.createOWLOntologyManager();
			ontology = man.loadOntologyFromOntologyDocument(ontologyFile);
			dataFactory = man.getOWLDataFactory();
		} catch (OWLOntologyCreationException e) {
			e.printStackTrace();
		}
		this.threshold = threshold;
	}
	
	public StatisticalFunctionalityDetector(InputStream is, double threshold) {
		try {
			OWLOntologyManager man = OWLManager.createOWLOntologyManager();
			ontology = man.loadOntologyFromOntologyDocument(is);
			dataFactory = man.getOWLDataFactory();
		} catch (OWLOntologyCreationException e) {
			e.printStackTrace();
		}
		this.threshold = threshold;
	}

	@Override
	public boolean isFunctional(String iri) {
		Set<OWLDataPropertyAxiom> axioms = ontology.getAxioms(dataFactory.getOWLDataProperty(IRI.create(iri)));
		if(!axioms.isEmpty()){
			Set<OWLAnnotation> annotations = axioms.iterator().next().getAnnotations(dataFactory.getOWLAnnotationProperty(confidencePropertyIRI));
			OWLLiteral val = (OWLLiteral) annotations.iterator().next().getValue();
			double confidence = Double.valueOf(val.parseDouble());
			if(confidence >= threshold){
				return true;
			} 
		}
		Set<OWLObjectPropertyAxiom> axioms2 = ontology.getAxioms(dataFactory.getOWLObjectProperty(IRI.create(iri)));
		if(!axioms.isEmpty()){
			Set<OWLAnnotation> annotations = axioms.iterator().next().getAnnotations(dataFactory.getOWLAnnotationProperty(confidencePropertyIRI));
			OWLLiteral val = (OWLLiteral) annotations.iterator().next().getValue();
			double confidence = Double.valueOf(val.parseDouble());
			if(confidence >= threshold){
				return true;
			} 
		}
		return false;
	}
	
	public static void main(String[] args) throws Exception{
		
//		String ontologyURL = "resources/dbpedia_3.8_enrichment.owl";
//		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
//		OWLDataFactory dataFactory = man.getOWLDataFactory();
//		OWLOntology ontology = man.loadOntologyFromOntologyDocument(new File(ontologyURL));
//		Set<OWLAxiom> functionalAxioms = new HashSet<OWLAxiom>();
//		for(OWLAxiom axiom : ontology.getAxioms(AxiomType.FUNCTIONAL_OBJECT_PROPERTY)){
//			functionalAxioms.add(axiom);
//		}
//		for(OWLAxiom axiom : ontology.getAxioms(AxiomType.FUNCTIONAL_DATA_PROPERTY)){
//			functionalAxioms.add(axiom);
//		}
//		OWLOntology functional = man.createOntology(IRI.create("http://sparql2nl.aksw.org/dbpedia"));
//		man.addAxioms(functional, functionalAxioms);
//		man.saveOntology(functional, new RDFXMLOntologyFormat(), new FileOutputStream(new File("resources/dbpedia_functional_axioms.owl")));
		StatisticalFunctionalityDetector detector = new StatisticalFunctionalityDetector(new File("resources/dbpedia_functional_axioms.owl"), 0.9);
		System.out.println(detector.isFunctional("http://dbpedia.org/ontology/occupation"));
		System.out.println(detector.isFunctional("http://dbpedia.org/ontology/birthDate"));
		System.out.println(detector.isFunctional("http://dbpedia.org/ontology/populationTotal"));
	}
	
	


}
