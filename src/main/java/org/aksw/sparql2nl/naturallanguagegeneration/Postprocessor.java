
package org.aksw.sparql2nl.naturallanguagegeneration;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import simplenlg.aggregation.ForwardConjunctionReductionRule;
import simplenlg.features.Feature;
import simplenlg.features.NumberAgreement;
import simplenlg.framework.*;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

/**
 *
 * @author christina (cunger@cit-ec.uni-bielefeld.de)
 */
public class Postprocessor {
    
    // SimpleNLG
    Lexicon lexicon;
    NLGFactory nlg;
    Realiser realiser;
    // Variables
    List<NPPhraseSpec> selects;
    Set<String> primaries;
    Set<String> secondaries;
    // Sentences
    Set<Sentence> sentences;
    Set<Union> unions;
    Set<Filter> filters;
    Set<Sentence> orderbylimit;
    // Aggregated result
    NLGElement output;
    NLGElement additionaloutput;
    boolean relativeClause;
    // Auxiliaries
    int id;
    HashMap<String,Set<Sentence>> hash;
    HashMap<String,String> equalities;
    boolean ask;
    // Debug
    boolean TRACE = false;
    
    
    public Postprocessor() {
        lexicon = Lexicon.getDefaultLexicon();
        nlg = new NLGFactory(lexicon);
        realiser = new Realiser(lexicon);
        selects = new ArrayList<NPPhraseSpec>();
        primaries = new HashSet<String>();
        secondaries = new HashSet<String>();
        sentences = new HashSet<Sentence>();
        unions = new HashSet<Union>();
        filters = new HashSet<Filter>();
        orderbylimit = new HashSet<Sentence>();
        output = null;
        additionaloutput = null;
        id = 0;
        hash = new HashMap<String,Set<Sentence>>();
        equalities = new HashMap<String,String>();
        ask = false;
        relativeClause = false;
    }
    
    public void flush() {
        selects = new ArrayList<NPPhraseSpec>();
        primaries = new HashSet<String>();
        secondaries = new HashSet<String>();
        sentences = new HashSet<Sentence>();
        unions = new HashSet<Union>();
        filters = new HashSet<Filter>();
        orderbylimit = new HashSet<Sentence>();
        output = null;
        additionaloutput = null;
        id = 0;
        hash = new HashMap<String,Set<Sentence>>();
        equalities = new HashMap<String,String>();
        ask = false;
        relativeClause = false;
    }
    
    public void addPrimary(String s) {
        primaries.add(s);
    }
    public void addSecondary(String s) {
        secondaries.add(s);
    }
    
    public void postprocess() {
              
        removeDuplicatesInSentences();
        removeDuplicatesInUnions();
        
//        if (primaries.isEmpty() && secondaries.isEmpty()) {
//            // nothing TODO, just verbalise
//        }
//        else { 
        
            if (ask) { 
                for (String p : primaries)   selects.add(nlg.createNounPhrase("?"+p));
                for (String s : secondaries) selects.add(nlg.createNounPhrase("?"+s));
            }
            
            // STAGE 1: SPhraseSpec level
            
            fuseWithSelects();
        
            if (TRACE) { System.out.println("\n--1-------------------------"); this.print(); }
            
            for (String var : primaries)   aggregateTypeAndLabelInformation("?"+var);
            for (String var : secondaries) aggregateTypeAndLabelInformation("?"+var);
            
            collectEqualities();
            addOrderbylimitToEqualities();
            
            if (TRACE) { System.out.println("\nEqualities:\n"); 
                         System.out.println(equalities.toString()); }
            
            for (Sentence s : sentences) addFilterInformation(s);
            flattenUnionsAndFilters();
            
            if (TRACE) { System.out.println("\n--2-------------------------"); this.print(); }
            
            setPassive();
            realiseOptionality();
            
            Set<String> bodyParts = new HashSet<String>();
            sentences = fuseObjectWithSubject(sentences,bodyParts); // this should be the last operation before flattening, as fused output is of type String            
            bodyParts.addAll(flattenAll());
               
            if (TRACE) { System.out.println("\n--3: FLATTENED------------------"); 
                         for (String s : bodyParts) System.out.println(s); }
            
            if (TRACE) { System.out.println("\n--EQUALITIES--------------------"); 
                         System.out.println(equalities.toString()); }
            
            // STAGE 2: String level ("A.I. is for people who not have regex skill." (@DEVOPS_BORAT))
            
            fuseWithSelectsAgain(bodyParts);            
            addRemainingStuffToEqualities(bodyParts);      
            replaceEqualities(bodyParts);
                     
            List<String> final_bodyParts = new ArrayList<String>(bodyParts);
            final_bodyParts = replaceVarOccurrencesByPronouns(final_bodyParts);
            final_bodyParts = order(final_bodyParts);
            final_bodyParts = replaceVarOccurencesByIndefinites(final_bodyParts);
            final_bodyParts = replaceVarOccurrencesByPronouns(final_bodyParts);
           
            if (TRACE) { System.out.println("\n--4-------------------------"); 
                         for (String s : final_bodyParts) System.out.println(s);
                         this.print(); }
            
            buildRelativeClause(final_bodyParts);
            contractAdditionalOutput();
            
            if (final_bodyParts.size() >= 1) {
                CoordinatedPhraseElement body = nlg.createCoordinatedPhrase();
                body.setConjunction("and");
                for (String b : final_bodyParts) body.addCoordinate(b);
                output = body;
            }
                               
            if (ask) checkSelects();
            
            polish();
            
            if (TRACE) {
                System.out.println("\n");
                if (output == null) System.out.println("Output: null");  
                else System.out.println("Output: " + realiser.realiseSentence(output)); 
                if (additionaloutput == null) System.out.println("Additional output: null");
                else System.out.println("Additional output: " + realiser.realiseSentence(additionaloutput)); 
            }
            
//        }
        
    }
    
    
    private void removeDuplicatesInSentences() {
        
        Set<Sentence> duplicates = new HashSet<Sentence>();
        Set<String> realisations = new HashSet<String>();
        
        for (Sentence s : sentences) {
            String realisation = realiser.realiseSentence(s.sps);
            if (realisations.contains(realisation)) duplicates.add(s);
            else realisations.add(realisation);     
        }
        
        sentences.removeAll(duplicates);
    }
    private void removeDuplicatesInUnions() {
 
        Set<Union> singleton_unions = new HashSet<Union>();
        
        for (Union union : unions) {
            Set<Sentence> duplicates = new HashSet<Sentence>();
            Set<String> realisations = new HashSet<String>();
        
            // collect duplicates
            for (Set<Sentence> un : union.sentences) {
                for (Sentence s : un) {
                   String realisation = realiser.realiseSentence(s.sps);
                   if (realisations.contains(realisation)) duplicates.add(s); 
                   else realisations.add(realisation); 
                }
            }
            // remove duplicates
            Set<Set<Sentence>> empty_uns = new HashSet<Set<Sentence>>();
            for (Set<Sentence> un : union.sentences) {
                un.removeAll(duplicates);
                if (un.isEmpty()) empty_uns.add(un);
            }
            union.sentences.removeAll(empty_uns);
            if (union.sentences.size() == 1) singleton_unions.add(union);
        }
        
        for (Union union : singleton_unions) {
            for (Set<Sentence> un : union.sentences) {
                sentences.addAll(un);
            }
        }
        unions.removeAll(singleton_unions);
    }
    
       
    private HashMap<String,Set<Sentence>> groupSentencesByVar() { 
       
        HashMap<String,Set<Sentence>> hash = new HashMap<String,Set<Sentence>>(primaries.size()+secondaries.size());
        
        Set<String> allVars = new HashSet<String>();
        allVars.addAll(primaries); allVars.addAll(secondaries);
        for (String var : allVars) {
             Set<Sentence> sents = collectAllSentencesContaining(var);
             hash.put(var,sents);
        }
        
        return hash;
    }
    
    private Set<Sentence> collectAllSentencesContaining(String var) {

        Set<Sentence> out = new HashSet<Sentence>();

        for (Sentence sentence : sentences) {
            addSentence(var,out,sentence);           
        }
        for (Union union : unions) {
            for (Set<Sentence> un : union.sentences) {          
                for (Sentence s : un) {
                    addSentence(var,out,s);
                }
            }
        }
        for (Filter f : filters) {
            for (Sentence s : f.sentences) {
                addSentence(var,out,s);
            }
        }
        for (Sentence s : orderbylimit) {
            addSentence(var,out,s);
        }
        
        return out;
    }
    
    private void addSentence(String var, Set<Sentence> out, Sentence s) {

            SPhraseSpec sentence = s.sps;
            
            String subjvar = sentence.getSubject().getFeatureAsString("head");
            String objvar  = "";
            if (sentence.getObject() != null)
                objvar = sentence.getObject().getFeatureAsString("head");    
            Pattern p = Pattern.compile(".*(\\?\\w+)(\\s|'|:).*");
            Matcher m = p.matcher(subjvar); 
            if (m.find()) subjvar = m.group(1);
            m = p.matcher(objvar); 
            if (m.find()) objvar = m.group(1);
            
            if (subjvar.contains("?"+var) || objvar.contains("?"+var)) 
                out.add(s);
    }

    
    private void collectEqualities() {
        
        Set<Sentence> delete = new HashSet<Sentence>();
        for (Sentence s : sentences) {
             if (getVerb(s.sps).equals("be")) {
                 String subj_str = realiser.realise(s.sps.getSubject()).toString();
                 String  obj_str = realiser.realise(s.sps.getVerbPhrase()).toString().replaceFirst("(is)|(are) ","").trim();
                 if (subj_str.startsWith("?") && !subj_str.contains("'")) {
                     if (!s.optional) {
                         if (equalities.containsKey(subj_str)){
                             equalities.put(subj_str,equalities.get(subj_str)+" and "+obj_str);
                         } else equalities.put(subj_str,obj_str);
                     }
                     delete.add(s);
                 } else if (obj_str.startsWith("?") && !obj_str.contains("'")) {
                     if (!s.optional) {
                         if (equalities.containsKey(obj_str)){
                             equalities.put(obj_str,equalities.get(obj_str)+" and "+subj_str);
                         } else equalities.put(obj_str,subj_str);
                     }
                     delete.add(s);
                 }
             }
        }
        sentences.removeAll(delete);
    }
    
    
    private void flattenUnionsAndFilters() {
        
        Set<Sentence> fused;
        Set<Union> delete = new HashSet<Union>();
        for (Union union : unions) {
            Sentence sent = union.removeRedundancy(realiser);
            if (sent != null) {
                sentences.add(sent);
                delete.add(union);
                break;
            }
            boolean flattened = true;
            Set<Sentence> flattened_parts = new HashSet<Sentence>();
            for (Set<Sentence> un : union.sentences) {  
                 fused = fuseSubjects(fuseObjects(un,"and"),"and");
                 if (fused.size() == 1) 
                     for (Sentence s : fused) flattened_parts.add(s);
                 else flattened = false;
            }
            if (flattened) {
                fused = fuseObjects(fuseSubjects(flattened_parts,"or"),"or");
                if (fused.size() == 1) {
                for (Sentence s : fused) {
                    sentences.add(s);
                    delete.add(union);
                }}
            }
        }
        unions.removeAll(delete);
        
        Set<Filter> delete_too = new HashSet<Filter>();
        for (Filter f : filters) {
             fused = fuseSubjects(fuseObjects(f.sentences,f.coord),f.coord);
             if (fused.size() == 1) {
                 for (Sentence s : fused) sentences.add(s);
                 delete_too.add(f);
             }
        }
        filters.removeAll(delete_too);
    }
    
       private Set<String> flattenAll() {
        
        Set<String> realisations = new HashSet<String>();
        
        for (Sentence s : sentences) {
             realisations.add(realiser.realise(s.sps).toString());
        }
        for (Filter f : filters) {
            CoordinatedPhraseElement c = nlg.createCoordinatedPhrase();
            c.setConjunction(f.coord);
            Set<NLGElement> fused = fuseRealisations(f.sentences,f.coord);
            if (fused.size() == 1) {
                for (NLGElement e : fused) realisations.add(realiser.realise(e).toString());
            }
            else {
                for (NLGElement e : fused) c.addCoordinate(e);
                realisations.add(realiser.realise(c).toString());
            }           
        }
        for (Union union : unions) {
            CoordinatedPhraseElement c = nlg.createCoordinatedPhrase();
            c.setConjunction("or");
            Set<NLGElement> conjuncts = new HashSet<NLGElement>();
            for (Set<Sentence> un : union.sentences) {
                Set<NLGElement> fused = fuseRealisations(un,"and");
                if (fused.size() == 1) {
                    for (NLGElement e : fused) conjuncts.add(e); 
                }
                else if (fused.size() > 1) {
                    CoordinatedPhraseElement c_in = nlg.createCoordinatedPhrase();
                    c_in.setConjunction("and");
                    for (NLGElement e : fused) c_in.addCoordinate(e);
                    conjuncts.add(c_in);
                }
            }
            Set<NLGElement> fused = fuseRealisationsToo(conjuncts,"or");
            if (fused.size() == 1) {
                 for (NLGElement e : fused) realisations.add(realiser.realise(e).toString());
            }
            else if (fused.size() > 1) {
                for (NLGElement e : fused) c.addCoordinate(e);
                realisations.add(removeDots(realiser.realise(c).toString()));
            }          
        }
        
        if (!orderbylimit.isEmpty()) {
            String addo = "";
            for (Sentence s : orderbylimit) 
                addo += realiser.realise(s.sps).toString()+". ";
            additionaloutput = nlg.createNLGElement(addo);
        }
        
        return realisations;
    }
       
   
    private void realiseOptionality() {
        
        for (Sentence s : sentences) {
            if (s.optional) {
                s.sps.setFeature("modal","may");
                s.optional = false;
            }
        }
        for (Filter f : filters) {
            for (Sentence s : f.sentences) {
                if (s.optional) {
                    s.sps.setFeature("modal","may");
                    s.optional = false;
                }
            }
        }
        for (Union union : unions) {
            for (Set<Sentence> un : union.sentences) {
                for (Sentence s : un) {
                     if (s.optional) {
                         s.sps.setFeature("modal","may");
                         s.optional = false;
                     }
                }
            }
        }
    }
    
    private void setPassive() {
        hash = groupSentencesByVar();
        String var = max(hash.keySet());
        if (hash.containsKey(var)) {
        for (Sentence s : hash.get(var)) {
             if (!realiser.realise(s.sps.getSubject()).toString().contains(var)) {
                 if (getVerb(s.sps).equals("be") && !s.sps.getFeatureAsBoolean("PASSIVE")) {
                     String subj = realiser.realise(s.sps.getSubject()).toString();
                     String obj  = realiser.realise(s.sps.getVerbPhrase()).toString().replace("is ","");
                     if (!obj.contains(" by ") && !obj.contains(" than ") && !obj.contains(" as ")) { 
                         // i.e. unless it's passive already or a comparison
                         if (obj.contains("?date") && !obj.contains(" on ?date"))
                              s.sps = nlg.createClause(subj,"be",obj.replace("?date","on ?date")); // dirty hack but works out nice
                         else s.sps = nlg.createClause(obj,"be",subj);
                     }
                 }
                 else if (!getVerb(s.sps).equals("have"))
                    s.sps.setFeature(Feature.PASSIVE,true);
             }
        }}
    }
    
    private void buildRelativeClause(List<String> final_bodyParts) {
        if (final_bodyParts.size() == 1 && selects.size() == 1) {
            String f = final_bodyParts.get(0);
            if (f.startsWith("they ") || f.startsWith("their ")) {
                String head = selects.get(0).getFeatureAsString("head");
                Pattern p = Pattern.compile("(.*) (and their .*)");
                Matcher m = p.matcher(head); 
                String new_f = f;
                if (f.startsWith("they "))       new_f = f.replaceFirst("they ","that ");
                else if (f.startsWith("their ")) new_f = f.replaceFirst("their ","whose ");
                if (m.find()) {
                    selects.get(0).setFeature("head",m.group(1)+" "+new_f+", "+m.group(2));
                    final_bodyParts.remove(f);
                }
                else {
                    final_bodyParts.remove(f);
                    final_bodyParts.add(new_f);
                    relativeClause = true;
                }
            }
        }
    }
    
    private void contractAdditionalOutput() {
        
        String addout = realiser.realise(additionaloutput).toString();
        String new_addout = null;
        
        Pattern order     = Pattern.compile("The results are in ((descending)|(ascending)) order\\.");
        Pattern order_wrt = Pattern.compile("The results are in ((descending)|(ascending)) order with respect to (.*)\\.");
        Pattern number    = Pattern.compile("The query returns the (.*) result(s)?\\.");
        Matcher m_order_wrt = order_wrt.matcher(addout);
        Matcher m_order     = order.matcher(addout);
        Matcher m_number    = number.matcher(addout);
        if (m_order_wrt.find() && m_number.find()) {
            new_addout = "The query returns the result with the ";
            if (!m_number.group(1).equals("1st") && !m_number.group(1).equals("first")) new_addout += m_number.group(1);
            if      (m_order_wrt.group(1).equals("descending")) new_addout += " highest ";
            else if (m_order_wrt.group(1).equals("ascending"))  new_addout += " lowest ";
            String wrt = m_order_wrt.group(4);
            if (wrt.startsWith("this their ")) wrt = wrt.replace("this their ","");
            if (wrt.startsWith("their "))      wrt = wrt.replace("their ","");
            if (wrt.startsWith("this "))       wrt = wrt.replace("this ","");
            if (wrt.startsWith("the "))        wrt = wrt.replace("the ","");
            if (wrt.contains("."))             wrt = wrt.substring(0,wrt.indexOf("."));
            new_addout += wrt;
        }
        else if (m_order.find() && m_number.find()) {
            new_addout = "The query returns the "; 
            if (!m_number.group(1).equals("1st") && !m_number.group(1).equals("first")) new_addout += m_number.group(1);
            if      (m_order.group(1).equals("descending")) new_addout += "highest ";
            else if (m_order.group(1).equals("ascending"))  new_addout += "lowest ";
            new_addout += " result.";
        }
        
        if (new_addout != null) additionaloutput = nlg.createNLGElement(new_addout);
    }
    
    
    
    private Set<Sentence> fuseObjects(Set<Sentence> sentences,String conjunction) {
        
        if (sentences.size() == 1) return sentences;
        
        Hashtable<String,Sentence> memory = new Hashtable<String,Sentence>();
        HashSet<Sentence> failed = new HashSet<Sentence>();
        
        for (Sentence sentence : sentences) {
            
            NLGElement subj = sentence.sps.getSubject();
            NLGElement obj  = sentence.sps.getObject();
            String key = removeDots(realiser.realise(sentence.sps).toString().replace(removeDots(realiser.realise(obj).toString()),""));
            
            // if subject and verb are the same, fuse objects
            if (memory.containsKey(key)) { 
                Sentence memelement = memory.get(key);               
                NLGElement newobj = fuse(obj,memelement.sps.getObject(),conjunction);                 
                if (newobj != null) memelement.sps.setObject(newobj); // change memelement and don't add sentence
                else failed.add(sentence); // unless objects cannot be fused (i.e. newobj == null)
            }
            else memory.put(key,sentence); // otherwise add sentence
        }
        
        failed.addAll(memory.values());
        return failed;
    }
    private Set<Sentence> fuseSubjects(Set<Sentence> sentences,String conjunction) {

        if (sentences.size() == 1) return sentences;
           
        Hashtable<String,Sentence> memory = new Hashtable<String,Sentence>();
        HashSet<Sentence> failed = new HashSet<Sentence>();
        
        for (Sentence sentence : sentences) {
            
            NLGElement subj = sentence.sps.getSubject();
            NLGElement obj  = sentence.sps.getObject();
            String key = removeDots(realiser.realise(sentence.sps).toString().replace(removeDots(realiser.realise(subj).toString()),""));
            
            // if verb+object of sentence is the same as one already encountered, fuse subjects
            if (memory.containsKey(key)) {
                Sentence memelement = memory.get(key);               
                NLGElement newsubj = fuse(subj,memelement.sps.getSubject(),conjunction); 
                if (newsubj != null) memelement.sps.setSubject(newsubj); // change memelement and don't add sentence
                else failed.add(sentence); // unless objects cannot be fused (i.e. newsubj == null)
            }
            else memory.put(key,sentence); // otherwise add sentence
        }
        
        failed.addAll(memory.values());
        return failed;
    }
    
    private Set<Sentence> fuseObjectWithSubject(Set<Sentence> sentences,Set<String> fused) {
        // SUBJ is ?x . ?x V  OBJ -> SUBJ V OBJ
        // SUBJ V  ?x . ?x is OBJ -> SUBJ V OBJ
        
        if (sentences == null) return new HashSet<Sentence>();
        if (sentences.size() == 1) return sentences;
                
        Sentence objsent  = null;
        Sentence subjsent = null;
        String object  = null;
        String subject = null;
        boolean subjIs = false;
        boolean objIs  = false;       
        
        loop:
        for (Sentence s : sentences) {
            if (s == null) {}
            else {
                object = getObject(s.sps);
                if (object != null) {
                    for (Sentence sent : sentences) {  
                        subject = getSubject(sent.sps);
                        if (subject != null && subject.equals(object)) {
                            if (getVerb(s.sps) != null && getVerb(s.sps).equals("be")) objIs = true;
                            if (getVerb(sent.sps) != null && getVerb(sent.sps).equals("be")) subjIs = true;
                            if ((objIs || subjIs) && !realiser.realiseSentence(sent.sps).contains(" not ")) {
                                if (!occursAnywhereElse(object) && s.optional == sent.optional) {
                                    objsent = s;
                                    subjsent = sent;
                                    break loop;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (objsent == null || subjsent == null || object == null || subject == null || object.equals(subject)) return sentences;
                        
        sentences.remove(objsent);
        sentences.remove(subjsent);
        if (objIs) {
            String newsent = realiser.realiseSentence(subjsent.sps).replace(object,removeDots(realiser.realiseSentence(objsent.sps)).replace(object,"").replace(" is ","").replace(" are ","").trim());
            newsent = removeDots(newsent);
            fused.add(newsent);
//            objsent.setVerb(subjsent.getVerb());
//            objsent.setObject(subjsent.getObject());
//            sentences.add(objsent);
        }
        else if (subjIs) {
            String newsent = realiser.realiseSentence(objsent.sps).replace(subject,removeDots(realiser.realiseSentence(subjsent.sps)).replace(subject,"").replace(" is ","").replace(" are ","").trim());
            newsent = removeDots(newsent);
            fused.add(newsent);
//            subjsent.setVerb(objsent.getVerb());
//            subjsent.setObject(objsent.getObject());
//            sentences.add(subjsent);
        }
        
        return fuseObjectWithSubject(sentences,fused);
    }
    
    private NLGElement fuse(NLGElement e1,NLGElement e2,String conjunction) {
                
        if (!e1.getCategory().equals(e2.getCategory())) return null; // cannot fuse elements of different category
                
        if (e1.getCategory().equals(PhraseCategory.NOUN_PHRASE)) {
            String[] real1 = e1.getFeatureAsString("head").split(" ");
            String[] real2 = e2.getFeatureAsString("head").split(" ");
                        
            // forwards
                String prefix = "";
                int lf = 0;
                for (int i = 0; i < Math.min(real1.length,real2.length); i++) {
                    if (real1[i].toLowerCase().equals(real2[i].toLowerCase())) prefix += " " + real1[i];
                    else { lf = i; break; }
                } 
                prefix = prefix.trim();

                if (lf != 0) {
                    String newhead1 = ""; 
                    String newhead2 = "";
                    String newhead = prefix + " ";
                    for (int i = lf; i < real1.length; i++) newhead1 += real1[i] + " ";
                    for (int i = lf; i < real2.length; i++) newhead2 += " " + real2[i];
                    if (!newhead1.trim().toLowerCase().equals(newhead2.trim().toLowerCase())) {
                        newhead += newhead1 + conjunction + newhead2;
                    } else newhead += newhead1;
                    e1.setFeature("head",newhead);
                    return e1;
                }        
            
            // backwards   
            if (real1.length == real2.length) {
                String postfix = "";
                int lb = 0;
                for (int i = real1.length-1; i >= 0; i--) {
                    if (real1[i].toLowerCase().equals(real2[i].toLowerCase())) {
                        postfix = real1[i] + " " + postfix;
                        lb++;
                    }
                    else break;
                } 
                postfix = postfix.trim();

                if (lb != 0) {
                    String newhead1 = "";
                    String newhead2 = "";
                    String newhead = "";
                    for (int i = 0; i < real1.length-lb; i++) newhead1 += real1[i] + " ";
                    for (int i = 0; i < real2.length-lb; i++) newhead2 += " " + real2[i];
                    if (!newhead1.trim().toLowerCase().equals(newhead2.trim().toLowerCase())) {
                        newhead += newhead1 + conjunction + newhead2;
                    } else newhead += newhead1;
                    newhead += " " + postfix;
                    e1.setFeature("head",newhead);
                    return e1;
                }        
            }
        }
        
        return null;
    }
    
    private Set<NLGElement> fuseRealisations(Set<Sentence> e,String conjunction) {
            
        Set<NLGElement> e_converted = new HashSet<NLGElement>();
        for (Sentence s : e) e_converted.add(s.sps);
        
        return fuseRealisationsToo(e_converted,conjunction);
    }
    
    private Set<NLGElement> fuseRealisationsToo(Set<NLGElement> e,String conjunction) {
        
        Set<NLGElement> result = new HashSet<NLGElement>();
        
        Set<NLGElement> used = new HashSet<NLGElement>();
        for (NLGElement s1 : e) {
             if (!used.contains(s1)) {
                 for (NLGElement s2 : e) {
                      if (s1 != s2 && !used.contains(s2)) {
                          NLGElement fused = fuseRealisationsOf(s1,s2,conjunction);
                          if (fused != null) {
                              result.add(fused);
                              used.add(s1); used.add(s2);
                              break;
                          }
                      }
                  }
             }
        }
        for (NLGElement s : e) {
            if (!used.contains(s)) result.add(s);
        }
        
        return result;
    }
    
    private NLGElement fuseRealisationsOf(NLGElement e1,NLGElement e2,String conjunction) {
                
        String[] real1 = realiser.realise(e1).toString().split(" ");
        String[] real2 = realiser.realise(e2).toString().split(" ");
            
        NLGElement result;
                        
        // forwards
        String prefix = "";
        int lf = 0;
        for (int i = 0; i < Math.min(real1.length,real2.length); i++) {
             if (real1[i].toLowerCase().equals(real2[i].toLowerCase())) prefix += " " + real1[i];
             else { lf = i; break; }
        } 
        prefix = prefix.trim();

        if (lf != 0) {
            String newhead1 = ""; 
            String newhead2 = "";
            String newhead = prefix + " ";
            for (int i = lf; i < real1.length; i++) newhead1 += real1[i] + " ";
            for (int i = lf; i < real2.length; i++) newhead2 += " " + real2[i];
            if (!newhead1.trim().toLowerCase().equals(newhead2.trim().toLowerCase())) {
                if (prefix.endsWith("'s")) newhead2 = " its" + newhead2;
                    newhead += newhead1 + conjunction + newhead2;
                } else newhead += newhead1;
                //result.setRealisation(newhead);
                result = nlg.createSentence(newhead);
                return result;
            }        
            
        // backwards   
        if (real1.length == real2.length) {
            String postfix = "";
            int lb = 0;
            for (int i = real1.length-1; i >= 0; i--) {
                 if (real1[i].toLowerCase().equals(real2[i].toLowerCase())) {
                     postfix = real1[i] + " " + postfix;
                     lb++;
                 }
                 else break;
            } 
            postfix = postfix.trim();

            if (lb != 0) {
                String newhead1 = "";
                String newhead2 = "";
                String newhead = "";
                for (int i = 0; i < real1.length-lb; i++) newhead1 += real1[i] + " ";
                for (int i = 0; i < real2.length-lb; i++) newhead2 += " " + real2[i];
                if (!newhead1.trim().toLowerCase().equals(newhead2.trim().toLowerCase())) {
                     newhead += newhead1 + conjunction + newhead2;
                } else newhead += newhead1;
                newhead += " " + postfix;
                //result.setRealisation(newhead);
                result = nlg.createSentence(newhead);
                return result;
            }        
        }
        
        return null;
    }
    
    private void addOrderbylimitToEqualities() {
        
        for (String var : equalities.keySet()) {
            
            if (equalities.get(var).matches("a \\w+")) {
                Pattern more  = Pattern.compile(" the number of (\\?"+var.substring(1)+") is greater than( or equal to)? ([0-9]+)");
                Pattern less  = Pattern.compile(" the number of (\\?"+var.substring(1)+") is less than( or equal to)? ([0-9]+)");
                Matcher m;
            
                Set<Sentence> used = new HashSet<Sentence>();
                for (Sentence s : orderbylimit) {
                     m = more.matcher(realiser.realise(s.sps).toString());
                     if (m.find()) {
                         equalities.put(var,"more than "+m.group(3)+" "+equalities.get(var).substring(2)+"s");
                         used.add(s); break;
                     }
                     m = less.matcher(realiser.realise(s.sps).toString());
                     if (m.find()) {
                         equalities.put(var,"less than "+m.group(3)+" "+equalities.get(var).substring(2)+"s");
                         used.add(s); break;
                     }
                }
                orderbylimit.removeAll(used);
            }   
        }
    }
    private void addRemainingStuffToEqualities(Set<String> bodyParts) {
        
        for (String var : equalities.keySet()) {

             Pattern label = Pattern.compile("\\?"+var.substring(1)+" has the ((label)|(title)|(name)) (.*)");
             Matcher m;
                
             String used = null;
             for (String s : bodyParts) {
                  m = label.matcher(s);
                  if (m.find()) {
                      equalities.put(var,equalities.get(var)+" with "+m.group(1)+" "+m.group(5));
                      used = s; break;
                  }
             }
             if (used != null) bodyParts.remove(used);
        }
    }
    
    private void replaceEqualities(Set<String> bodyParts) {
        
        Set<String> deleteThese = new HashSet<String>();
        Set<String> addThese    = new HashSet<String>();
        Set<String> used_keys   = new HashSet<String>();
        HashMap<String,String> indirectly_used = new HashMap<String,String>();
                
        for (String var : equalities.keySet()) {
                        
            // first within equalities
            for (String v : equalities.keySet()) {
                 String value = equalities.get(v);
                 if (value.matches(".*\\?"+var.substring(1)+"(\\'|\\s|\\z).*")) {
                     if (value.matches(".*\\?"+var.substring(1)+"\\'s? .*")) {
                         value = value.replace(var+"'s ","the ").replace(var+"' ","the ")+" of "+var;
                     }
                     equalities.put(v,value.replace(var,equalities.get(var)));
                     used_keys.add(var);
                     indirectly_used.put(v,value.replace(var,"this "+equalities.get(var).replace("this a ","this ")));
//                     Matcher m = Pattern.compile(".*( with the (.*)?((label)|(title)|(name)).*)\\z").matcher(equalities.get(var));
//                     if (m.find()) equalities.put(var,equalities.get(var).replace(m.group(1),""));
                 }
            }
                                                
            // then in selects
            for (NPPhraseSpec sel : selects) {
                String head = sel.getFeatureAsString("head");
                if (head.matches(".*\\?"+var.substring(1)+"(\\s|\\z)") && !head.contains("'") && !isNeeded(var,bodyParts,1) && !equalities.get(var).contains("?")) {
                    sel.setFeature("head",head.replace(var,equalities.get(var)).replace(" their "," its "));
                    sel.setPreModifier("");
                    used_keys.add(var);
//                    Matcher m = Pattern.compile(".*( with the (.*)?((label)|(title)|(name)).*)\\z").matcher(equalities.get(var));
//                    if (m.find()) equalities.put(var,equalities.get(var).replace(m.group(1),""));
                }
            }
            
            // and finally in bodyParts
            Pattern p = Pattern.compile("(\\?"+var.substring(1)+")(\\s|\\z|\\.|\\,|\\')");
            for (String s : bodyParts) {
                 Matcher m = p.matcher(s);
                 String old_s = s;
                 boolean found = false;
                 while (m.find()) {
                     if (!s.matches(".* has the \\w*\\s?((label)|(title)|(name)) .*")
                             && !equalities.get(var).matches(".* with the \\w*\\s?((label)|(title)|(name)) .*") && !s.contains("'")) {
                        found = true;
                        if (used_keys.contains(var)) { 
                            String repl = equalities.get(var);
                            if (repl.startsWith("a ") || repl.startsWith("an ")) repl = repl.replace("a ","").replace("an ","");
                            s = s.replace(m.group(1),"this "+repl); }
                        else if (indirectly_used.keySet().contains(var)) {    
                            String repl = indirectly_used.get(var);
                            if (repl.startsWith("a ") || repl.startsWith("an ")) repl = "this "+repl.replace("a ","").replace("an ","");
                            s = s.replace(m.group(1),repl); }
                        else {
                            s = s.replace(m.group(1),equalities.get(var));}
                     }
                 }
                 if (found) {
                     addThese.add(s);
                     deleteThese.add(old_s);
                     used_keys.add(var);
//                     m = Pattern.compile(".*( with the (.*)?((label)|(title)|(name)).*)\\z").matcher(equalities.get(var));
//                     if (m.find()) equalities.put(var,equalities.get(var).replace(m.group(1),""));
                     NPPhraseSpec delete = null;
                     for (NPPhraseSpec sel : selects) {
                          if (sel.getFeatureAsString("head").equals(var)) {
                              delete = sel; 
                              break;
                          }
                     }
                     if (delete != null) selects.remove(delete);
                 }
            }
            bodyParts.removeAll(deleteThese); deleteThese = new HashSet<String>();
            bodyParts.addAll(addThese);       addThese    = new HashSet<String>();
            
            // and orderbylimits
            if (additionaloutput != null) {
                String o = realiser.realise(additionaloutput).toString();
                Pattern number = Pattern.compile(" the number of (\\?"+var.substring(1)+") ");
                Matcher m = number.matcher(o);
                if (m.find()) {
                    String repl = equalities.get(var);
                    if (repl.matches("a \\w+")) repl = repl.substring(2)+"s";
                    o = o.replace(m.group(1),repl);
                    additionaloutput = nlg.createNLGElement(o);
                    used_keys.add(var);
                }
                else {
                    m = p.matcher(o);
                    if (m.find()) {
                        String repl;
                        if (indirectly_used.containsKey(var)) repl = indirectly_used.get(var);
                        else repl = equalities.get(var);
                        if (used_keys.contains(var)) {
                            if (repl.startsWith("a ") || repl.startsWith("an ")) repl = repl.replace("a ","").replace("an ","");
                            else repl = "this "+repl;
                        }
                        o = o.replace(m.group(1),repl);
                        additionaloutput = nlg.createNLGElement(o); 
                        used_keys.add(var);
                    }
                }
            }
        }
        
        equalities.keySet().removeAll(used_keys);
        for (String var : equalities.keySet()) {
             bodyParts.add(var+" is "+equalities.get(var));
        }
    }
    
    
    private List<String> order(List<String> bodyParts) {
               
        List<String> primary_declaratives   = new ArrayList<String>();
        List<String> primary_others         = new ArrayList<String>();
        List<String> secondary_declaratives = new ArrayList<String>();
        List<String> secondary_others       = new ArrayList<String>();
        
        for (String v : primaries) {
            for (String s : bodyParts) {
                if (s.startsWith("?"+v+" is ") || s.startsWith("they are ")) 
                    primary_declaratives.add(s); 
                else if (s.contains("?"+v)) 
                    primary_others.add(s); 
            }
            bodyParts.removeAll(primary_declaratives);
            bodyParts.removeAll(primary_others);
        }
               
        for (String v : secondaries) {
            for (String s : bodyParts) {
                 if (s.startsWith("?"+v+" is ")) secondary_declaratives.add(s); 
                 else if (s.contains("?"+v))     secondary_others.add(s); 
            }
            bodyParts.removeAll(secondary_declaratives);
            bodyParts.removeAll(secondary_others);
        }
        
        List<String> ordered_bodyParts = new ArrayList<String>();
        ordered_bodyParts.addAll(primary_declaratives);
        ordered_bodyParts.addAll(primary_others);
        ordered_bodyParts.addAll(secondary_declaratives);
        ordered_bodyParts.addAll(secondary_others);
        ordered_bodyParts.addAll(bodyParts);
                
        return ordered_bodyParts;
    }
    
    private List<String> replaceVarOccurrencesByPronouns(List<String> bodyParts) {
        
        List<String> result  = new ArrayList<String>();
        
        Set<String> vars = new HashSet<String>();
        Pattern p = Pattern.compile("(^|.*)(\\?([\\w]*))(\\s|\\z|\\.|\\,|\\')");
        Matcher m;
        for (NPPhraseSpec sel : selects) {
            m = p.matcher(realiser.realise(sel).toString());
            if (m.find()) vars.add(m.group(2));
        }
        for (String s : bodyParts) {
            m = p.matcher(s);
            while (m.find()) vars.add(m.group(2));
        }
                        
        String followup = "(\\s|\\z|\\.|\\,)";
        if (vars.size() == 1) {
            for (String var : vars) {
            String p_var_s = "\\?"+var.substring(1)+"'(s)? ";
            String p_var   = "\\?"+var.substring(1)+followup;
            boolean inSelects = false;
            for (NPPhraseSpec sel : selects) {
                 String head = sel.getFeatureAsString("head");
                 if (head.contains(var)) {
                     sel.setFeature("head",head.replace(var,""));
                     if (realiser.realise(sel).toString().isEmpty()) sel.setFeature("head","entities"); // happens in the case of ASK queries, where selects have no premodifier
                     inSelects = true;
                     break;
                 }
            }
            for (int i = 0; i < bodyParts.size(); i++) {
                 String b = bodyParts.get(i);
                 Matcher m_p_var   = Pattern.compile(p_var).matcher(b);
                 Matcher m_p_var_s = Pattern.compile(p_var_s).matcher(b);
//                 if (i == 0 && !inSelects) {
//                     if      (m_p_var_s.find()) b = b.replaceFirst(p_var_s,"");
//                     else if (m_p_var.find())   b = b.replaceFirst(p_var,"");
//                 }
//                 m_p_var   = Pattern.compile(p_var).matcher(b);
//                 m_p_var_s = Pattern.compile(p_var_s).matcher(b);
                 if (m_p_var.find()) {
                     b = b.replaceAll(p_var,"they ");
                     b = b.replaceAll("they is ","they are ").replaceAll("they has ","they have ");
                 } 
                 if (m_p_var_s.find()) { 
                     b = b.replaceAll(p_var_s,"their ");
                     b = b.replaceAll(" by they"," by them").replaceAll("they(\\s)?(\\.)?\\z","them");
                 }
                 result.add(b);
            }
            if (additionaloutput != null)
                additionaloutput = nlg.createNLGElement(realiser.realise(additionaloutput).toString().replaceAll(p_var_s,"their ").replaceAll(p_var,"they "));
            }
        }
        else result.addAll(bodyParts);
                
        return result;
    }
    
    private List<String> replaceVarOccurencesByIndefinites(List<String> bodyParts) {
        
            List<String> result  = new ArrayList<String>();
            List<String> changed = new ArrayList<String>();
            HashMap<String,Integer> order = new HashMap<String,Integer>();
            for (int i = 0; i < bodyParts.size(); i++) {
                 order.put(bodyParts.get(i),i);
                 result.add(i,null);
            }
            HashMap<String,Boolean> descriptions = new HashMap<String,Boolean>();
            
            for (String s : secondaries) {
                 String var = "?"+s;
                 if (!realiser.realise(additionaloutput).toString().matches(".*\\?"+s+"(\\'|\\s|\\z).*")) {
                    int count = 0;
                    for (String b : bodyParts) if (b.matches(".*\\?"+s+"(\\'|\\s|\\z).*")) count++;
                    if (count > 0) {
                        boolean firstOccurence = false;
                        boolean initial = true;
                        for (String b : bodyParts) {
                            int pos = order.get(b);
                            String new_b = "";
                            if (b.matches(".*\\?"+s+"(\\'|\\s|\\z).*")) {
                                if (initial && !firstOccurence) { firstOccurence = true; initial = false; }
                                else if (!initial && firstOccurence) { firstOccurence = false; }
                                if (count == 1) {
                                    changed.add(b);
                                    String d;
                                    if (var.length() > 2) d = var.substring(1);
                                    else                  d = "entity";
                                    if (!descriptions.containsKey(d)) {
                                        new_b = b.replace(var,"some "+d);
                                        descriptions.put(d,false);
                                    }
                                    else if (!descriptions.get(d)) {
                                        descriptions.put(d,true);
                                        new_b = b.replace(var,"some other "+d);
                                    }
                                }
                                else if (count > 1 && secondaries.size() == 1) {                                   
                                    changed.add(b);
                                    String d;
                                    if (var.length() > 2) d = var.substring(1);
                                    else                  d = "entity";
                                    if (firstOccurence)  {
                                        if (!descriptions.containsKey(d)) {
                                            new_b = b.replaceAll("\\?"+s,"some "+d);
                                            descriptions.put(var,false);
                                        }
                                        else if (!descriptions.get(d)) {
                                            descriptions.put(d,true);
                                            new_b = b.replaceAll("\\?"+s,"some other "+d);
                                        }
                                    }
                                    if (!descriptions.containsKey(d)) {
                                        new_b = b.replaceAll("\\?"+s,"this "+d);
                                        descriptions.put(var,false);
                                        }
                                    else if (!descriptions.get(d)) {
                                        descriptions.put(d,true);
                                        new_b = b.replaceAll("\\?"+s,"this other "+d);
                                    }
                                }
                                if (!new_b.isEmpty()) result.add(pos,new_b);
                            }
                        }
                    }
                }
            }
            
            for (String b : bodyParts) if (!result.contains(b) && !changed.contains(b)) result.add(order.get(b),b);
            
            List<String> resultresult = new ArrayList<String>();
            for (String r : result) if (r != null) resultresult.add(r);
            
            return resultresult;
    }
    
    
    private void polish() {
                
        Pattern missing_s = Pattern.compile("[i,o,u,y,a,e]'(\\s)");
        Pattern wrong_s   = Pattern.compile("s'(s) ");
        Pattern missing_n = Pattern.compile(" (a) [i,I,o,O,u,U,a,A,e,E]");
        Pattern wrong_n   = Pattern.compile(" (an) (?! [i,I,o,O,u,U,a,A,e,E])");
        Matcher m;

        for (NPPhraseSpec sel : selects) {
             String head = sel.getFeatureAsString("head");
             if (head.contains("ignorecase")) sel.setHead(head.replace("ignorecase","(ignoring case)"));
        }
        if (output != null) {
            String out = realiser.realise(output).toString();
            out = out.replace(" this a "," this ").replace(" this an "," this ").replace(" this the "," this ").replace(" this their "," their ");
            out = out.replace(" they is "," they are ").replace(" they has "," they have ").replace(" is they "," is them ").replace(" by they"," by them").replace(" on they"," on them").replace(" they,"," them,").replace(" they."," them.");
            out = out.replace("ignorecase","(ignoring case)");
            out = out.replace("%28","(").replace("%29",")").replace(". and"," and").replace(". or"," or");
            out = out.replace("  "," ");
            m = missing_s.matcher(out);
            if (m.find()) out = out.replace(m.group(1),"s");
            m = wrong_s.matcher(out);
            if (m.find()) out = out.replace(m.group(1),"");
            m = missing_n.matcher(out);
            if (m.find()) out = out.replace(m.group(1),"an");
            m = wrong_n.matcher(out);
            if (m.find()) out = out.replace(m.group(1),"a");
            out = out.replace("  "," ");
            output = nlg.createNLGElement(out);
        }
        
        if (additionaloutput != null) {
            String addout = realiser.realise(additionaloutput).toString();
            addout = addout.replace(" this a "," this ").replace(" this an "," this ").replace(" this the "," this ").replace(" this their "," their ");
            addout = addout.replace(" they is "," they are ").replace(" they has "," they have ").replace(" is they "," is them ");
            addout = addout.replace("ignorecase","(ignoring case)");
            m = missing_s.matcher(addout);
            if (m.find()) addout = addout.replace(m.group(1),"s");
            m = wrong_s.matcher(addout);
            if (m.find()) addout = addout.replace(m.group(2),"");
            m = missing_n.matcher(addout);
            if (m.find()) addout = addout.replace(m.group(1),"an");
            m = wrong_n.matcher(addout);
            if (m.find()) addout = addout.replace(m.group(1),"a");
            addout = addout.replace("  "," ");
            additionaloutput = nlg.createNLGElement(addout);
        }
        
    }
    
    private void checkSelects() {
        
        Set<NPPhraseSpec> unnecessary = new HashSet<NPPhraseSpec>();
        for (NPPhraseSpec sel : selects) {
            String s = realiser.realise(sel).toString();
            if (!s.contains("?") && realiser.realise(output).toString().contains(s)) {
                unnecessary.add(sel);
                output = nlg.createNLGElement(realiser.realise(output).toString().replaceFirst(" this "," "));
            }
        }
        selects.removeAll(unnecessary);
    }
    


    
    
    private String getSubject(NLGElement el) {
        if (el == null) return null;
        if (el.getFeature("subjects") != null) {
            ArrayList<NLGElement> subjects = new ArrayList<NLGElement>(((Collection<NLGElement>) el.getFeature("subjects")));
            if (subjects != null && !subjects.isEmpty()) {
                if (subjects.get(0).getFeature("head") != null) {
                    return subjects.get(0).getFeature("head").toString();
                }
            }
        }
        else if (el.hasFeature("coordinates")) {
            for (NLGElement c : ((Collection<NLGElement>) el.getFeature("coordinates"))) {
               if (c.getFeature("subjects") != null) {
                    ArrayList<NLGElement> subjects = new ArrayList<NLGElement>(((Collection<NLGElement>) c.getFeature("subjects")));
                    if (subjects != null && !subjects.isEmpty()) {
                        if (subjects.get(0).getFeature("head") != null) {
                            return subjects.get(0).getFeature("head").toString();
                        }
                    }
                } 
            }
        }
        return null;
    }
    private String getObject(NLGElement el) {
        if (el == null) return null;
        if (el.hasFeature("verb_phrase") && ((NLGElement) el.getFeature("verb_phrase")).hasFeature("complements")) {
            ArrayList<NLGElement> objects = new ArrayList<NLGElement>(((Collection<NLGElement>) ((NLGElement) el.getFeature("verb_phrase")).getFeature("complements")));
            if (objects != null && !objects.isEmpty()) {
                if (objects.get(0).getFeature("head") != null) {
                    return objects.get(0).getFeature("head").toString();
                }
            }
        }
        else if (el.hasFeature("coordinates")) {
            for (NLGElement c : ((Collection<NLGElement>) el.getFeature("coordinates"))) {
                if (c.hasFeature("verb_phrase") && ((NLGElement) c.getFeature("verb_phrase")).hasFeature("complements")) {
                    ArrayList<NLGElement> objects = new ArrayList<NLGElement>(((Collection<NLGElement>) ((NLGElement) c.getFeature("verb_phrase")).getFeature("complements")));
                    if (objects != null && !objects.isEmpty()) {
                        if (objects.get(0).getFeature("head") != null) {
                            return objects.get(0).getFeature("head").toString();
                        }
                    }
                }
            }
        }
        return null;
    }
    private String getVerb(NLGElement el) {
        NLGElement vp = ((NLGElement) el.getFeature("verb_phrase")); 
        if (vp != null) {
            return ((WordElement) vp.getFeature("head")).getBaseForm();
        }
        return null;
    }
    
    
    
    
    
    
    private void aggregateTypeAndLabelInformation(String var) {
        // for now ignoring unions and assuming there are not multiple type and label information
        
        Sentence type  = null; 
        Sentence label = null;
        Sentence name  = null;
        
        // collect the Sentences verbalizing type, label, and name
        for (Sentence sentence : sentences) {
            SPhraseSpec s = sentence.sps;
            String sstring = removeDots(realiser.realise(s).toString());
            if ((sstring.startsWith(var+"\'s type is ") || sstring.startsWith(var+"\' type is ")) && !sstring.startsWith(var+"\'s type is ?")) {
                type = sentence; 
            }
            else if (sstring.startsWith(var+"\'s label is ") || sstring.startsWith(var+"\' label is ")) {
                label = sentence; 
            }
            else if (sstring.startsWith(var+"\'s name is ") || sstring.startsWith(var+"\' name is ")) {
                name = sentence; 
            }
        } 
        
        if (type !=null || label != null || name != null) {
        // build a single sentence containing all those information
        SPhraseSpec newsentence = nlg.createClause();
        newsentence.setSubject(var);
        newsentence.setFeature(Feature.NUMBER,NumberAgreement.SINGULAR);
        NPPhraseSpec objnp = null;
        NPPhraseSpec np = null;
        if (label != null || name != null) {
            String noun = ""; 
            boolean already = false;
            if (label != null) {
                String sstring = removeDots(realiser.realise(label.sps).toString());
		String lang = checkLanguage(label.sps.getObject().getFeatureAsString("head"));
		if (lang != null) noun += lang + " ";
		String pattern;
		if (var.endsWith("s"))
                    pattern = var + "\' label is ";
		else
                    pattern = var + "\'s label is ";
		if (sstring.replace(pattern,"").startsWith("?"))
                    noun += "label " + sstring.replace(pattern,"");
		else
                    noun += "label \"" + sstring.replace(pattern,"") + "\"";               
                // removal
                if (label.optional) noun += " (if it exists)";
		already = true;
                sentences.remove(label);
            }
            if (name != null) { 
                if (already) noun += " and ";
                String sstring = removeDots(realiser.realise(name.sps).toString());
                String lang = checkLanguage(name.sps.getObject().getFeatureAsString("head"));
                if (lang != null) noun += lang + " ";
                String pattern;
                if (var.endsWith("s")) pattern = var+"\' name is "; 
                else pattern = var+"\'s name is "; 
                if (sstring.replace(pattern,"").startsWith("?")) noun += "name " + sstring.replace(pattern,"");
                else noun += "name \"" + sstring.replace(pattern,"") + "\"";  
                // removal
                if (name.optional) noun += " (if it exists)";
                already = true;
                sentences.remove(name);
            }
            
            np = nlg.createNounPhrase(); 
            np.setHead("the "+noun);
        }
        
        if (type != null) {
            String sstring = removeDots(realiser.realise(type.sps).toString());
            String pattern;
            if (var.endsWith("s")) pattern = var+"\' type is "; 
            else pattern = var+"\'s type is "; 
            String classstring = sstring.replace(pattern,"");
            String determiner;
            if (Pattern.matches("[a,i,e,u,o,A,I,E,U,O].*",classstring)) determiner = "an";
            else determiner = "a";
            objnp = nlg.createNounPhrase(determiner,classstring);
            if (np != null) objnp.addPostModifier(("with " + removeDots(realiser.realise(np).toString())));
            newsentence.setVerb("be");
            newsentence.setObject(objnp);
            // removal
            sentences.remove(type); 
        }
        else {
            newsentence.setVerb("have");
            newsentence.setObject(np);
        }
        
        // TODO if type optional
        // newsentence.setFeature("modal","may");
        sentences.remove(name); sentences.remove(type); sentences.remove(label);
        sentences.add(new Sentence(newsentence,false,id)); id++;
        }
    }
    
    private String checkLanguage(String var) {
        
         String out = null;

         Filter usedfilter = null;
         for (Filter f : filters) {
             for (Sentence s : f.sentences) {
                  String fstring = removeDots(realiser.realise(s.sps).toString());
                  if (fstring.startsWith(var+" is in English")) {
                      out = "English";
                      usedfilter = f;
                      String remainder = fstring.replace(var+" is in English","");
                      if (remainder.trim().startsWith("and") || remainder.trim().startsWith("And")) 
                          remainder = remainder.replaceFirst("and","").replaceFirst("And","").trim();
                      // TODO if (!remainder.isEmpty()) f.sentences.add(nlg.createClause(remainder)); // dhould not happen if filter.sentences are atomic
                      break;
                  }
            }
         }
         filters.remove(usedfilter);
         
         if (out != null) return out;
        
         Set<Sentence> language = new HashSet<Sentence>();
         Set<Sentence> usedlanguage = new HashSet<Sentence>();

            for (Sentence sentence : sentences) {
                SPhraseSpec s = sentence.sps;
                if (realiser.realiseSentence(s).startsWith(var+" is in ")) 
                    language.add(sentence);
            }

            for (Sentence lang : language) {
                String subj = lang.sps.getSubject().getFeatureAsString("head");
                if (subj.equals(var)) {
                    out = removeDots(realiser.realiseSentence(lang.sps).replace(var+" is in ",""));
                    usedlanguage.add(lang);
                    break;
                }
            }
            sentences.removeAll(usedlanguage);
         
            return out;        
    }
    
    private void addFilterInformation(Sentence sentence) {
        
        String obj  = removeDots(realiser.realise(sentence.sps.getObject()).toString());
        Pattern p = Pattern.compile(".*(\\?([\\w]*))\\s?\\z");
        
        String[] comparison = {" is greater than or equal to ",
                                           " is greater than ",
                                           " is less than ",
                                           " is less than or equal to "};
        
        // attach filter information to object
        Matcher m = p.matcher(obj);
        if (m.find()) { // i.e. if the object is a variable at the end of the phrase
            Set<Filter> usedFilters = new HashSet<Filter>();
            String var = m.group(1);
            String newhead = obj;
            for (Filter f : filters) {
                Set<Sentence> used = new HashSet<Sentence>();
                for (Sentence s : f.sentences) {
                    String fstring = removeDots(realiser.realise(s.sps).toString());
                    if (fstring.startsWith(var + " matches ")) {
                        String match = fstring.replace(var+" matches ","");
                        if (((WordElement) sentence.sps.getVerb()).getBaseForm().equals("be")
                                && !occursAnywhereElse(obj)) {
                            sentence.sps.setVerb("match");
                            newhead = match;
                        } 
                        else newhead = obj.replace(m.group(1),m.group(1) + " matching " + match);
                        if (newhead.contains("ignorecase")) {
                            newhead = newhead.replace("ignorecase","");
                            newhead += " (ignoring case)";
                        }
                        used.add(s);
                    }
                    else if (fstring.startsWith(var + " does not exist")) {
                        if (((WordElement) sentence.sps.getVerb()).getBaseForm().equals("be")) {
                            if (!occursAnywhereElse(obj)) {
                                sentence.sps.setVerb("do not exist");
                                newhead = "";
                            }
                            else {
                                newhead = removeDots(realiser.realise(sentence.sps.getSubject()).toString());
                                sentence.sps.setSubject("no "+obj);
                            }                        
                        }
                        else sentence.sps.setFeature("negated",true);
                        used.add(s);
                    }
                    else {
                        boolean compmatch = false;
                        for (String comp : comparison) {
                            if (fstring.startsWith(var + comp)) {
                                compmatch = true;
                                String match = fstring.replace(var + comp,"");
                                if (!occursAnywhereElse(obj)
                                    && ((WordElement) sentence.sps.getVerb()).getBaseForm().equals("be")) {
                                    newhead = obj.replace(m.group(1),comp.replace(" is ","") + match);
                                } else {
                                    newhead = obj.replace(m.group(1),m.group(1) + " which" + comp + match);
                                }
                                used.add(s);
                                break;
                            }
                        }
                        if (!compmatch && fstring.split(" ")[0].equals(obj)) {
                            // SUBJ is ?x . ?x V OBJ -> SUBJ V OBJ
                            if (((WordElement) sentence.sps.getVerb()).getBaseForm().equals("be")
                                    && !sentence.sps.getFeatureAsBoolean("negated")
                                    && fstring.startsWith(obj)
                                    && !occursAnywhereElse(obj)) {   
                                newhead = fstring.replace(obj,"").trim();
                            }
                            else newhead += " which " + fstring.replace(obj,"");
                            sentence.sps.setVerb(nlg.createNLGElement("")); // verb is contained in object (newhead)
                            used.add(s);
                        }
                    }
                }
                f.sentences.removeAll(used);
                if (f.sentences.isEmpty()) usedFilters.add(f);
            }
            sentence.sps.getObject().setFeature("head",newhead);
            filters.removeAll(usedFilters);
        }

        // attach filter information to subject
        String subj = removeDots(realiser.realise(sentence.sps.getSubject()).toString());
        p = Pattern.compile("(^|\\A)(\\?([\\w]*))((?!')||\\z)");
        m = p.matcher(subj);
        if (m.find()) { // i.e. if the subject is a variable at the beginning of the phrase
            Set<Filter> usedFilters = new HashSet<Filter>();
            String var = m.group(2);
            for (Filter f : filters) {
                Set<Sentence> used = new HashSet<Sentence>();
                for (Sentence s : f.sentences) {
                    String fstring = removeDots(realiser.realise(s.sps).toString());
                    if (fstring.startsWith(var+" does not exist")) {
                        used.add(s);
                        if (inSelects(subj)) sentence.sps.setFeature("negated",true);
                        else sentence.sps.setSubject("no " + removeDots(realiser.realise(sentence.sps.getSubject()).toString()));
                    }
                    else if (fstring.startsWith(var)) {
                        sentence.sps.addComplement("and " + fstring.replace(var,"").trim());
                        used.add(s);
                    }
                }
                f.sentences.removeAll(used);
                if (f.sentences.isEmpty()) usedFilters.add(f);
            }
        filters.removeAll(usedFilters);
        }       
    }
    
    private boolean inSelects(String var) {
        for (NPPhraseSpec sel : selects) {
            if (realiser.realise(sel).toString().endsWith(var)) return true;
        }
        return false;
    }
    
    private void fuseWithSelects() {
        
        HashMap<NPPhraseSpec,NPPhraseSpec> replacements = new HashMap<NPPhraseSpec,NPPhraseSpec>();
        Set<SPhraseSpec> delete = new HashSet<SPhraseSpec>();
        String selstring;
        String var;
        int oc;
        for (NPPhraseSpec sel : selects) {
            selstring = removeDots(realiser.realise(sel).toString());
            var = selstring.substring(selstring.indexOf("?")+1);
            oc = numberOfOccurrences(var);
                        
            if (oc == 0) sel.setFeature("head","");
            else if (oc == 1) {
                SPhraseSpec del = null;
                for (Sentence sentence : sentences) {
                    SPhraseSpec s = sentence.sps;
                    // selects TYPE ?x such that ?x is OBJECT -> selects OBJECT
                    if (((WordElement) s.getVerb()).getBaseForm().equals("be")
                            && realiser.realiseSentence(s).matches(".* (is)|(are) \\?"+var+"\\.")) {
                        NPPhraseSpec repl = null;
                        if (realiser.realise(s.getSubject()).toString().equals("?"+var)) 
                            repl = nlg.createNounPhrase(s.getObject());
                        else if (realiser.realise(s.getObject()).toString().equals("?"+var)) 
                            repl = nlg.createNounPhrase(s.getSubject());
                        if (repl != null) {
                            if (realiser.realise(sel).toString().contains(" number of ")) {
                                repl.setPlural(true); // .setFeature(Feature.NUMBER,NumberAgreement.PLURAL);
                                repl.addPreModifier("the number of ");
                            }
                            replacements.put(sel,repl);
                            delete.add(s); break;
                        }
                    }
                }
            }
        }
        for (NPPhraseSpec key : replacements.keySet()) {
            selects.remove(key);
            selects.add(replacements.get(key));
        }
        sentences.removeAll(delete);
    }
    
    private void integrateLabelInfoIntoSelects(Set<NLGElement> bodyparts) {
        
        Pattern p = Pattern.compile("(\\?\\w*)((('s)? (.*))?and( \\?\\w*)?)? ((has)||(may have)) the((\\s\\w*)? ((label)||(name))) (\\?[\\w]*)( and(.*))?\\.?");
        NLGElement info = null;
        NLGElement rest = null;
        for (NLGElement bodypart : bodyparts) {
        if (bodypart != null) {
            String bstring = removeDots(realiser.realiseSentence(bodypart));
            Matcher m = p.matcher(bstring);
            if (m.matches() && inSelects(m.group(1)) && inSelects(m.group(15))) {
                boolean labelvarfree = true;
                for (NLGElement b : bodyparts) {
                    if (b != null && !b.equals(bodypart)) {
                        if (realiser.realiseSentence(b).matches("(\\A|(.*\\s))\\"+m.group(15)+"(\\s|\\z).*")) {
                        labelvarfree = false;
                        break;
                    }}
                }
                if (labelvarfree) {
                    info = bodypart; 
                    String restrealization = "";
                    String part2 = null;
//                    boolean part2sel = false;
//                    boolean part2postmod = false;
                    if (m.group(2) != null) {
                        part2 = m.group(2);
                        if (m.group(2).endsWith(" and "+m.group(1))) part2 = part2.substring(0,part2.lastIndexOf(" and "));
                        if (part2.endsWith(" and")) part2 = part2.substring(0,part2.length()-4);
//                        if (m.group(16) == null) {
//                            if (part2.matches(".* is ([\\w,\\s])+ by .*")) {
//                                part2 = part2.replace(" is "," are ");
//                                part2 = "that"+part2;
//                                part2postmod = true;
//                            }
//                            else if (part2.matches(".* is .*")) {
//                                part2 = part2.replace(" is ","");
//                                part2sel = true;
//                            }
//                        }
//                        else 
                            restrealization += m.group(1)+part2;
                    }
                    if (m.group(16) != null) { 
                        if (restrealization.isEmpty()) { 
                            if (m.group(17).trim().startsWith(m.group(1))) restrealization += m.group(17);
                            else restrealization += m.group(1) + m.group(17);
                        }
                        else restrealization += " and "+m.group(17);
                    }    
                    else if (!restrealization.isEmpty()) { // conjunction before label info will miss an 'and', unless we add it again
                        String[] restrelparts = restrealization.split(", ");
                        restrealization = "";
                        for (int i = 0; i < restrelparts.length; i++) {
                            restrealization += restrelparts[i];
                            if (i == restrelparts.length-2) restrealization += " and ";
                            else if (i < restrelparts.length-2) restrealization += ", ";
                        }
                    }
                    if (!restrealization.isEmpty()) {
                        if (restrealization.endsWith(" and")) restrealization = restrealization.substring(0,restrealization.length()-4);
                        rest = nlg.createNLGElement(restrealization);
                    }
                    removeFromSelects(m.group(15));
                    for (NPPhraseSpec sel : selects) {
                        if (sel.getFeatureAsString("head").equals(m.group(1))) {
                            boolean keepuri = false;
                            if (rest != null) keepuri = true;
                            else {
                                for (NLGElement b : bodyparts) {
                                    if (b != null && !b.equals(bodypart)) {
                                        if (realiser.realiseSentence(b).matches("(\\A|(.*\\s))\\"+m.group(1)+"(\\s|\\z|').*")) {
                                            keepuri = true;
                                            break;
                                    }}
                                }
                                if (realiser.realiseSentence(additionaloutput).matches("(\\A|(.*\\s))\\"+m.group(1)+"(\\s|\\z|').*")) keepuri = true;
                            }
                            if (!keepuri) sel.setHead("");
//                            if (part2postmod) sel.addPostModifier(part2);
//                            else if (part2sel) {
//                                NPPhraseSpec part2head = nlg.createNounPhrase(part2);
//                                part2head.setPlural(true);
//                                sel.setHead(part2head);
//                                if (sel.hasFeature("premodifiers")) sel.setFeature("premodifiers",new HashSet<NLGElement>());
//                            }
                            String pron = "their";
                            if (sel.hasFeature("premodifiers")) {
                                List<NLGElement> premods = new ArrayList<NLGElement>((Collection) sel.getFeature("premodifiers"));
                                if (!premods.isEmpty() && premods.get(0).hasFeature("number")) {
                                    if (premods.get(0).getFeatureAsString("number").equals("SINGULAR")) pron = "its";
                                }
                            }
                            String postmodifier = "and " + pron + m.group(10);
                            if (m.group(7).equals("may have")) postmodifier += " (if it exists)";
                            sel.addPostModifier(postmodifier);
                        }
                    }
                }
            }
        }}
        bodyparts.remove(info);
        if (rest != null) bodyparts.add(rest);
    }
    
    private void removeFromSelects(String var) {
        List<NPPhraseSpec> newselects = new ArrayList<NPPhraseSpec>();
        for (NPPhraseSpec sel : selects) {
            if (!sel.getFeatureAsString("head").equals(var)) newselects.add(sel);
        }
        selects = newselects;
    }
    
    private void fuseWithSelectsAgain(Set<String> bodyParts) {
        
        Pattern p = Pattern.compile("(\\?\\w*) has the (\\w*\\s?((label)|(title)|(name))) (\\?\\w*)( (?! matching)(.*))?");
        Matcher m = null;
        String var = null; String type = null; String value = null; String rest = null;
        String part = null;
        for (String b : bodyParts) {
             m = p.matcher(b);
             if (m.find()) {
                 var   = m.group(1);
                 type  = m.group(2);
                 value = m.group(7);
                 rest  = m.group(8);
                 part  = b;
                 break;
             }
        }

        if (var != null && type != null && value != null) {
            NPPhraseSpec delete = null;
            for (NPPhraseSpec sel : selects) {
                 if (sel.getFeatureAsString("head").equals(var)) {
                     String new_head = var;
                     String[] rests = null;
                     Set<String> used = new HashSet<String>();
                     if (rest != null) {
                         rests = rest.split(" and ");
                         for (String r : rests) {
                              if (r.startsWith("is ")) {
                                  new_head += r.replace("is "," ");
                                  used.add(r);
                              }
                         }
                     }
                     new_head += " and their "+type;
                     if (isNeeded(value,bodyParts,1)) new_head += " "+value;
                     if (rests != null) {
                         for (String r : rests) {
                              if (!used.contains(r)) new_head += r;
                         }
                     }

                     sel.setFeature("head",new_head);
                     bodyParts.remove(part);
                 }
                 else if (sel.getFeatureAsString("head").equals(value)) {
                      delete = sel;
                 }
             }
             if (delete != null) selects.remove(delete);
        }        
        
        if (selects.size() == 1) {
            String head = selects.get(0).getFeatureAsString("head");
            if (!head.contains("'")) {
                 m = Pattern.compile("(\\?\\w+)(\\s|\\z|\\')").matcher(head);
                 if (m.find() && !isNeeded(m.group(1),bodyParts,0)) {
                     selects.get(0).setFeature("head",head.replace(m.group(1),"")); 
                 }
            }
        }
    }
    
    public NLGElement returnSelect() {
        if (selects.size() == 1) {
            return selects.get(0);
        } else {
            CoordinatedPhraseElement cpe = nlg.createCoordinatedPhrase(selects.get(0), selects.get(1));
            if (selects.size() > 2) {
                for (int i = 2; i < selects.size(); i++) {
                    cpe.addCoordinate(selects.get(i));
                }
            }
            return cpe;
        } 
    }
    
    private boolean occursAnywhereElse(String var) { 
        hash = groupSentencesByVar();
        if (hash.containsKey(var))
            return hash.get(var).size() > 1;
        else return false;
    }
 
    private int numberOfOccurrences(String var) {
        hash = groupSentencesByVar();
        if (hash.containsKey(var)) 
            return hash.get(var).size();
        else return 0;
    }
    
    private boolean isNeeded(String var,Set<String> bodyParts,int offset) {
        
        int sent_count = 0;
        for (String b : bodyParts) {
             if (b.contains(var+" ") || b.contains(var+"'") || b.contains(var+",") || b.contains(var+"."))
                 sent_count++;
        }       
        for (String k : equalities.keySet()) {
             if (k.equals(var) || equalities.get(k).contains(var)) sent_count++;
        }
        for (Sentence s : orderbylimit) {
             String o = realiser.realiseSentence(s.sps);
             if (o.contains(var+" ") || o.contains(var+"'") || o.contains(var+",") || o.contains(var+".")) 
                 sent_count++;
        }
        if (sent_count > offset) return true;
        else return false;
    }
    

    private String max(Set<String> vars) {
        String out = null;
        int beatme = 0;
        int beatmyfilter = 0;
        for (String s : vars) {
            if (numberOfOccurrences(s) >= beatme) {
                out = s;
                beatme = numberOfOccurrences(s);
            }
        }
        return out;
    }
    
    public String removeDots(String s) {
        s = s.replaceAll("\\.\\,",",").replaceAll("\\. and"," and");
        if (s.endsWith(".")) 
            return removeDots(s.substring(0,s.length()-1));
        else if (s.endsWith(". ")) 
            return removeDots(s.substring(0,s.length()-2));
        else return s;
    }
    
    private void cleanUp() { // very stupid Java programming
        
        Set<String> primariesLeft = new HashSet<String>();
        Set<String> secondariesLeft = new HashSet<String>();
        
        for (String var : primaries) {
            cleanUpVar(var,primariesLeft);
        }
        for (String var : secondaries) {
            cleanUpVar(var,secondariesLeft);
        }
        
        primaries = primariesLeft;
        secondaries = secondariesLeft;
    }
    private void cleanUpVar(String var,Set<String> varLeft) {
        boolean found = false;
            for (Sentence s : sentences) {
                if (realiser.realiseSentence(s.sps).contains(var)) {
                    varLeft.add(var);
                    found = true;
                    break;
                }
            }
            if (!found) {
                for (Union union : unions) {
                     for (Set<Sentence> un : union.sentences) {
                          for (Sentence s : un) {
                               if (realiser.realiseSentence(s.sps).contains(var)) {
                                   varLeft.add(var);
                                   break;
                               }
                          }
                     }
                }
            }
    }
    
    
    public void print() {
        
        String maxP = max(primaries);
        String maxS = max(secondaries);
        System.out.print("\nPrimary variables: ");
        for (String s : primaries) {
            if (s.equals(maxP)) System.out.print("!");
            System.out.print(s + "("+numberOfOccurrences(s)+") ");
        }
        System.out.print("\nSecondary variables: ");
        for (String s : secondaries) {
            if (s.equals(maxS)) System.out.print("!");
            System.out.print(s + "("+numberOfOccurrences(s)+") ");
        }
        System.out.println("\nSelects:"); 
        for (NPPhraseSpec sel : selects) {
            System.out.println(" -- " + realiser.realise(sel).toString());
        }
        System.out.println("\nSentences: ");
        for (Sentence s : sentences) {
            System.out.println(" -- " + s.optional + " -- " + realiser.realiseSentence(s.sps));
        }
        System.out.println("Unions: ");
        for (Union union : unions) {
            System.out.println("--union--\n");
            for (Set<Sentence> un : union.sentences) {
                System.out.print("--union part--\n");
                for (Sentence s : un) {
                     System.out.print(" -- " + s.optional + " -- " + realiser.realiseSentence(s.sps) + "\n    ");
                }
            }
            System.out.print("\n");
        }
        System.out.println("Filters: ");
        for (Filter f : filters) {
            for (Sentence s : f.sentences) {
                 System.out.print(" -- " + s.optional + " -- " + realiser.realiseSentence(s.sps) + "\n");
            }
        }
        System.out.println("Orderbylimits: ");
        for (Sentence s : orderbylimit) {
            System.out.print(" -- " + s.optional + " -- " + realiser.realiseSentence(s.sps) + "\n");
        }
    }
    
    private void printHash(HashMap<String,Set<Sentence>> hash) {
        System.out.println("\n -- hash --\n");
        for (String var : hash.keySet()) {
            System.out.println(var);
            for (Sentence s : hash.get(var)) {
                 System.out.print(" -- " + s.optional + " -- " + realiser.realiseSentence(s.sps) + "\n");
            }
        }
    }

}
