import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NormalizedNamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TrueCaseAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TrueCaseTextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Servlet implementation class CoreNLP
 */
public class CoreNLP extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private StanfordCoreNLP nlp;
	
    /**
     * @see HttpServlet#HttpServlet()
     */
    public CoreNLP() {
        super();
        
        //initialize the parser with all properties
      	Properties props = new Properties();
      	props.put("annotators", "tokenize, cleanxml, ssplit, pos, lemma, ner, regexner, truecase, parse, dcoref");
      	nlp = new StanfordCoreNLP(props);
    }

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		PrintWriter out = response.getWriter();
		String text = request.getParameter("freeText");

		//parse the string and output results
		Annotation document = new Annotation(text);
		nlp.annotate(document);
		
		JSONArray coreOutput = new JSONArray();
		JSONArray jsonSentences = new JSONArray();
		
		JSONArray wordList = new JSONArray(); //key all of the words by their index in the sentence, map to JSON Object w/ word, pos, NER
		
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		int sentenceId = 1;
		
		for(CoreMap sentence: sentences) {
			
			JSONArray jsonSentence = new JSONArray();
			for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
				
			}
		      for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
		    	  String word = token.word();
		    	  String pos = token.get(PartOfSpeechAnnotation.class);
		    	  String ne = token.get(NamedEntityTagAnnotation.class);
		    	  String normed = token.get(NormalizedNamedEntityTagAnnotation.class);
		    	  String lemma = token.lemma();
		    	  String trueCase = token.get(TrueCaseAnnotation.class);
		    	  String trueCaseText = token.get(TrueCaseTextAnnotation.class);
		    	  
		    	  int wordIndex = token.index();
		    	  
		    	  JSONObject obj = new JSONObject();
		    	  obj.put("index", wordIndex);
		    	  obj.put("word", word);
		    	  obj.put("lemma", lemma);
		    	  obj.put("pos", pos);
		    	  obj.put("ner", ne);
		    	  obj.put("norm_ner", normed);
		    	  obj.put("sentId", sentenceId);
		    	  wordList.add(obj);
		      }
		      
		      jsonSentence.add(wordList);

		      SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
		      
		      //for each sentence, build the linked dependency structure
		      try {
		    	  JSONArray depArr = new JSONArray();

			      if(dependencies != null && dependencies.getFirstRoot() != null) {
				      Collection<IndexedWord> words = dependencies.descendants(dependencies.getFirstRoot());
				      
				      Collection<SemanticGraphEdge> edges = dependencies.edgeListSorted();
				      for(SemanticGraphEdge e : edges) {
				    	  
				    	  JSONObject obj = new JSONObject();
				    	  obj.put("edge", e.getRelation().getShortName());
				    	  obj.put("from", e.getDependent().index() );
				    	  obj.put("to", e.getGovernor().index());
				    	  obj.put("sentId", sentenceId);
				    	  depArr.add(obj);
				      }
				      
			      }
			      
			      jsonSentence.add(depArr);
			      jsonSentences.add(jsonSentence);
			      sentenceId++;
			      
		      }catch(Exception e) {}
		}
		
		coreOutput.add(jsonSentences);
		
		JSONArray jsonCorefs = new JSONArray();
		
		Map<Integer, CorefChain> graph = document.get(CorefChainAnnotation.class);
		for(Map.Entry<Integer, CorefChain> entry : graph.entrySet()) {
			CorefMention topMention = entry.getValue().getRepresentativeMention();
			int topPosition = topMention.mentionID;
			
			JSONArray corefChain = new JSONArray();
			
			List<CorefMention> mentions = entry.getValue().getCorefMentions();
			for(CorefMention cm : mentions) {
				
				JSONObject obj = new JSONObject();
				obj.put("span", cm.mentionSpan);
		    	obj.put("sentId", cm.sentNum);
		    	boolean isBest = false;
		    	if(cm.mentionID == topPosition) isBest = true;
		    	obj.put("isBest", isBest);
		    	obj.put("animacy", cm.animacy.name());
		    	obj.put("gender", cm.gender.name());
		    	obj.put("mentionType", cm.mentionType.name());
		    	obj.put("number", cm.number.name());
		    	obj.put("startIndex", cm.startIndex);
		    	obj.put("endIndex", cm.endIndex);
		    	
		    	corefChain.add(obj);
			}
			
			jsonCorefs.add(corefChain);
		}
		
		coreOutput.add(jsonCorefs);
		
		StringWriter outWriter = new StringWriter();
		coreOutput.writeJSONString(outWriter);
		String jsonText = outWriter.toString();
		
		out.println(jsonText);
	}
}
