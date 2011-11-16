package org.aksw.sparql2nl.corpuscreation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ObjectOutputStream;
import java.util.Hashtable;
import java.util.Set;

import org.dllearner.algorithm.tbsl.sparql.Template;
import org.dllearner.algorithm.tbsl.templator.Templator;


public class Kreator {

	public static String[] FILES = {"resources/TREC_questions_2006.txt","resources/TREC_questions_2007.txt","resources/TREC_questions_2008.txt","resources/TREC_questions_2009.txt"};
	public static String[] LEXICON = {"resources/english.lex"};
	public static String TARGET = "target/corpus.out";

	
	public static void main(String[] args) {

		Templator temp = new Templator(false); // Templator without WordNet and with VERBOSE=false
		temp.setGrammarFiles(LEXICON);
		
		Set<Template> templates;
		
		BufferedReader in;
//		BufferedWriter out;
		ObjectOutputStream oos;
		
		int total = 0;
		int parsed = 0;
		
		try {
//			out = new BufferedWriter(new FileWriter("target/parsetest.txt"));
			oos = new ObjectOutputStream(new FileOutputStream(new File(TARGET)));

			Hashtable<Template,String> corpus = new Hashtable<Template,String>();
				
			for (String f : FILES) {
				in = new BufferedReader(new FileReader(f));
				String line;
				while ((line = in.readLine()) != null && !line.startsWith("//")) {
					
					try {
						templates = temp.buildTemplates(line);						
						total++;
						
						// PARSE TEST OUTPUT
//						if (!templates.isEmpty()) {
//							System.out.println("["+templates.size()+"] " + line);
//							out.write("\n["+templates.size()+"] " + line);
//							parsed++;
//						}
//						else {
//							System.out.println("[-] " + line);
//							out.write("\n[-] " + line);
//						}
						//
						
						// KORPUS GENERIERUNG
						if (!templates.isEmpty()) {
							parsed++;					
							for (Template t : templates) {
								corpus.put(t,line);
							}
							System.out.println("["+templates.size()+"] " + line);
						}
						//
					}
					catch (OutOfMemoryError e) {
						continue;
					}
					catch (NullPointerException e) {
						continue;
					}
				}
				in.close();
			}
			System.out.println("\n\nTotal: " + total + "\nParsed: " + parsed);
			System.out.println("Corpus size: " + corpus.size());
//			out.write("\n\nTotal: " + total + "\nParsed: " + parsed);
//			out.close();
			oos.writeObject(corpus);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}