/**
 * 
 */
package org.aksw.sparql2nl.rest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.aksw.sparql2nl.naturallanguagegeneration.TripleConverter;
import org.apache.jena.riot.Lang;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.SparqlEndpoint;

import simplenlg.lexicon.Lexicon;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

/**
 * @author Lorenz Buehmann
 *
 */
@Path("/sparql2nl")
public class RESTService {
	
	private static final Logger logger = Logger.getLogger(RESTService.class.getName());
	private static final Lexicon lexicon = Lexicon.getDefaultLexicon();
	
	public RESTService() {
		
	}
	
	@GET
	@Path("/test")
	@Produces(MediaType.TEXT_PLAIN)
	public String test() {
		return "test";
	}
	
	@POST
	@Context
	@Path("/triple2nl")
	@Produces(MediaType.APPLICATION_JSON)
	public String convertTriples(@Context ServletContext context, @FormParam("endpoint") String endpointURL, @FormParam("data") String data) {
		logger.info("REST Request - Converting triples");
		logger.info("Endpoint:" + endpointURL);
		logger.info("data:" + data);
		try {
			SparqlEndpoint endpoint = new SparqlEndpoint(new URL(endpointURL));
			
			//load triples
			Model model = ModelFactory.createDefaultModel();
			try(InputStream is = new ByteArrayInputStream(data.getBytes())){
				model.read(is, null, Lang.TURTLE.getLabel());
			} catch (IOException e) {
				e.printStackTrace();
			}
			logger.info(model.size() + " triples:");
			try(ByteArrayOutputStream baos = new ByteArrayOutputStream()){
				model.write(baos, "TURTLE");
				logger.info(new String(baos.toString("UTF-8")));
			} catch (IOException e) {
				e.printStackTrace();
			}
			Set<Triple> triples = new HashSet<Triple>((int) model.size());
			StmtIterator iterator = model.listStatements();
			while (iterator.hasNext()) {
				Statement statement = (Statement) iterator.next();
				triples.add(statement.asTriple());
			}
			
			//convert to text
			if(!model.isEmpty()){
				TripleConverter converter = new TripleConverter(endpoint, context.getRealPath("cache"), lexicon);
				String text = converter.convertTriplesToText(triples);
				logger.info("Text:" + text);
				return text;
			}
			
		} catch (MalformedURLException e) {
			logger.error("Malformed endpoint URL " + endpointURL);
		}
		
		logger.info("Done.");
		return null;
 
	}
	
	@GET
	@Context
	@Path("/sparql2nl")
	@Produces(MediaType.APPLICATION_JSON)
	public String convertSPARQL(@Context ServletContext context, @QueryParam("endpoint") String endpointURL, @QueryParam("query") String sparqlQuery) {
		logger.info("REST Request");
		
		
		logger.info("Done.");
		return null;
	}
}
