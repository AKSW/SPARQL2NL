package org.aksw.sparql2nl.naturallanguagegeneration;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.kb.sparql.SparqlQuery;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.util.SimpleIRIShortFormProvider;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import com.hp.hpl.jena.vocabulary.XSD;

public class URIConverter {
	
	private SimpleIRIShortFormProvider sfp = new SimpleIRIShortFormProvider();
	private SparqlEndpoint endpoint;
	
	public URIConverter(SparqlEndpoint endpoint) {
		this.endpoint = endpoint;
	}
	
	public String convert(String uri){
		if (uri.equals(RDF.type.getURI())) {
            return "type";
        } else if (uri.equals(RDFS.label.getURI())) {
            return "label";
        }
        try {
            String labelQuery = "SELECT ?label WHERE {<" + uri + "> "
                    + "<http://www.w3.org/2000/01/rdf-schema#label> ?label. FILTER (lang(?label) = 'en' )}";

            // take care of graph issues. Only takes one graph. Seems like some sparql endpoint do
            // not like the FROM option.
            ResultSet results = executeSelect(labelQuery);

            //get label from knowledge base
            String label = null;
            QuerySolution soln;
            while (results.hasNext()) {
                soln = results.nextSolution();
                // process query here
                {
                    label = soln.getLiteral("label").getLexicalForm();
                }
            }
            if(label == null && !uri.startsWith(XSD.getURI())){
            	label = dereferenceURI(uri);
            }
            if(label == null){
            	label = sfp.getShortForm(IRI.create(uri));
            }
            //if it is a number we attach "Number"
            if(uri.equals(XSD.nonNegativeInteger.getURI()) || uri.equals(XSD.integer.getURI())
            		|| uri.equals(XSD.negativeInteger.getURI()) || uri.equals(XSD.decimal.getURI())
            		|| uri.equals(XSD.xdouble.getURI()) || uri.equals(XSD.xfloat.getURI())
            		|| uri.equals(XSD.xint.getURI()) || uri.equals(XSD.xshort.getURI())
            		|| uri.equals(XSD.xbyte.getURI()) || uri.equals(XSD.xlong.getURI())
            		){
            	label += "Value";
            }
            	
            
            if(label == null){
            	label = uri;
            }
            return label;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
	}
	
	 /**
     * Returns the English label of the URI by dereferencing its URI and searching for rdfs:label entries.
     * @param uri
     * @return
     */
    private String dereferenceURI(String uri){
    	//TODO add caching for vocabulary
    	String label = null;
    	try {
			URLConnection conn = new URL(uri).openConnection();
			conn.setRequestProperty("Accept", "application/rdf+xml");
			Model model = ModelFactory.createDefaultModel();
			InputStream in = conn.getInputStream();
			model.read(in, null);
			for(Statement st : model.listStatements(model.getResource(uri), RDFS.label, (RDFNode)null).toList()){
				label = st.getObject().asLiteral().getLexicalForm();
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	return label;
    }
    
    private ResultSet executeSelect(String query){
    	QueryEngineHTTP qexec = new QueryEngineHTTP(endpoint.getURL().toString(), query);
    	qexec.setDefaultGraphURIs(endpoint.getDefaultGraphURIs());
    	ResultSet rs = qexec.execSelect();
    	return rs;
    }

}
