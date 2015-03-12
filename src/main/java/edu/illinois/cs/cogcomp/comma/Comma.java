package edu.illinois.cs.cogcomp.comma;

import edu.illinois.cs.cogcomp.core.datastructures.IQueryable;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.edison.features.helpers.WordHelpers;
import edu.illinois.cs.cogcomp.edison.sentences.*;
import edu.illinois.cs.cogcomp.edison.utilities.EdisonException;

import java.io.Serializable;
import java.util.*;

/**
 * A data structure containing all the information related to a comma.
 */
public class Comma implements Serializable {
    private String[] sentence;
    private String role;
    public int commaPosition;
    private TextAnnotation goldTA;
    private TextAnnotation TA;
    private static boolean GOLD, NERlexicalise, POSlexicalise;

    private static final long serialVersionUID = 715976951486905421l;

    /**
     * A default constructor used during training.
     * @param commaPosition The token index of the comma
     * @param role The gold-standard role of the comma
     * @param sentence The tokenized string of the sentence
     * @param TA The TextAnnotation containing all required views (POS, SRL, NER, etc)
     */
    public Comma(int commaPosition, String role, String sentence, TextAnnotation TA) {
        this.commaPosition = commaPosition;
        if (role != null) {
            if (role.equals("Entity attribute")) this.role = "Attribute";
            else if (role.equals("Entity substitute")) this.role = "Substitute";
            else this.role = role;
        }
        this.sentence = sentence.split("\\s+");
        this.TA = TA;
        CommaProperties properties = CommaProperties.getInstance();
        GOLD = properties.useGold();
        NERlexicalise = properties.lexicaliseNER();
        POSlexicalise = properties.lexicalisePOS();
    }

    /**
     * A constructor used during training, if gold-standard feature annotations are available.
     * @param commaPosition The token index of the comma
     * @param role The gold-standard role of the comma
     * @param sentence The tokenized string of the sentence
     * @param TA The TextAnnotation containing all required views (POS, SRL, NER, etc)
     * @param goldTA The TextAnnotation containing gold-standard views for training
     */
    public Comma(int commaPosition, String role, String sentence, TextAnnotation TA, TextAnnotation goldTA) {
        this(commaPosition, role, sentence, TA);
    	this.goldTA = goldTA;
    }

    /**
     * A constructor used at test time (for prediction only). Assumes no gold label;
     * @param commaPosition The token index of the comma
     * @param sentence The tokenized string of the sentence
     * @param TA The TextAnnotation containing all required views (POS, SRL, NER, etc)
     */
    public Comma(int commaPosition, String sentence, TextAnnotation TA) {
        this(commaPosition, null, sentence, TA);
        // Since we know there is going to be no gold structures available
        GOLD = false;
    }

    public String getRole() {
        return role;
    }

    public String getWordToRight(int distance) {
        // Dummy symbol for sentence end (in case comma is the second to last word in the sentence)
        if (commaPosition + distance >= sentence.length)
            return "###";
        return sentence[commaPosition + distance];
    }

    public String getWordToLeft(int distance) {
        // Dummy symbol for sentence start (in case comma is the second word in the sentence)
        if (commaPosition - distance < 0)
            return "$$$";
        return sentence[commaPosition - distance];
    }
    
    public String getPOSToLeft(int distance){
		TokenLabelView posView;
		if (GOLD)
			posView = (TokenLabelView) goldTA.getView(ViewNames.POS);
		else
			posView = (TokenLabelView) TA.getView(ViewNames.POS);
		return posView.getLabel(commaPosition - distance);
	}
    
    public String getPOSToRight(int distance){
    	TokenLabelView posView;
		if (GOLD)
			posView = (TokenLabelView) goldTA.getView(ViewNames.POS);
		else
			posView = (TokenLabelView) TA.getView(ViewNames.POS);
    	return posView.getLabel(commaPosition + distance);
    }

    public Constituent getChunkToRightOfComma(int distance){
    	SpanLabelView chunkView;
    	if (GOLD)
    		chunkView = (SpanLabelView) goldTA.getView(ViewNames.SHALLOW_PARSE);
    	else
    		chunkView = (SpanLabelView) TA.getView(ViewNames.SHALLOW_PARSE);
    	
		List<Constituent> chunksToRight= chunkView.getSpanLabels(commaPosition+1, TA.getTokens().length);
		Collections.sort(chunksToRight, new Comparator<Constituent>() {
			@Override
			public int compare(Constituent o1, Constituent o2) {
				return o1.getStartSpan() - o2.getStartSpan();
			}
		});
		
		Constituent chunk;
		if(distance<=0 || distance>chunksToRight.size())
			chunk = null;
		else 
			chunk = chunksToRight.get(distance-1);
		return chunk;
    }
    
    public Constituent getChunkToLeftOfComma(int distance){
    	SpanLabelView chunkView;
    	if (GOLD)
    		chunkView = (SpanLabelView) goldTA.getView(ViewNames.SHALLOW_PARSE);
    	else
    		chunkView = (SpanLabelView) TA.getView(ViewNames.SHALLOW_PARSE);
		
		List<Constituent> chunksToLeft = chunkView.getSpanLabels(0, commaPosition+1);
		System.out.println(chunksToLeft);
		Collections.sort(chunksToLeft, new Comparator<Constituent>() {
			@Override
			public int compare(Constituent o1, Constituent o2) {
				return o2.getStartSpan() - o1.getStartSpan();
			}
		});
		
		Constituent chunk;
		if(distance<=0 || distance>chunksToLeft.size())
			chunk = null;
		else 
			chunk = chunksToLeft.get(distance-1);
		return chunk;
    }

    public Constituent getPhraseToLeftOfComma(int distance){
    	TreeView parseView;
    	if (GOLD)
    		parseView = (TreeView) goldTA.getView(ViewNames.PARSE_GOLD);
    	else
    		parseView = (TreeView) TA.getView(ViewNames.PARSE_STANFORD);
		Constituent comma = getCommaConstituentFromTree(parseView);

        return getSiblingToLeft(distance, comma, parseView);
    }
    
    public Constituent getPhraseToRightOfComma(int distance){
    	TreeView parseView;
    	if (GOLD)
    		parseView = (TreeView) goldTA.getView(ViewNames.PARSE_GOLD);
    	else
    		parseView = (TreeView) TA.getView(ViewNames.PARSE_STANFORD);
		Constituent comma = getCommaConstituentFromTree(parseView);

        return getSiblingToRight(distance, comma, parseView);
    }
    
    public Constituent getPhraseToLeftOfParent(int distance){
    	TreeView parseView;
    	if (GOLD)
    		parseView = (TreeView) goldTA.getView(ViewNames.PARSE_GOLD);
    	else
    		parseView = (TreeView) TA.getView(ViewNames.PARSE_STANFORD);
		Constituent comma = getCommaConstituentFromTree(parseView);
		Constituent parent = TreeView.getParent(comma);
        return getSiblingToLeft(distance, parent, parseView);
    }

    public Constituent getPhraseToRightOfParent(int distance){
    	TreeView parseView;
    	if (GOLD)
    		parseView = (TreeView) goldTA.getView(ViewNames.PARSE_GOLD);
    	else
    		parseView = (TreeView) TA.getView(ViewNames.PARSE_STANFORD);
		Constituent comma = getCommaConstituentFromTree(parseView);
		Constituent parent = TreeView.getParent(comma);
        return getSiblingToRight(distance, parent, parseView);
    }

    public Constituent getCommaConstituentFromTree(TreeView parseView){
		Constituent comma = null;
		for(Constituent c: parseView.getConstituents()){
			if(c.isConsituentInRange(commaPosition, commaPosition+1)){
				try {
					comma = parseView.getParsePhrase(c);
				} catch (EdisonException e) {
					e.printStackTrace();
					System.exit(1);
				}
				break;
			}
		}
		return comma;
    }
    
    public Constituent getSiblingToLeft(int distance, Constituent c, TreeView parseView){
    	Constituent leftSibling = c;
    	IQueryable<Constituent> siblings = parseView.where(Queries.isSiblingOf(c));
    	while(distance-- > 0){
    		Iterator<Constituent> leftSiblingIt = siblings.where(Queries.adjacentToBefore(leftSibling)).iterator();
    		if(leftSiblingIt.hasNext())
    			leftSibling = leftSiblingIt.next();
    		else
    			return null;
    	}
    	return leftSibling;
    }
    
    public Constituent getSiblingToRight(int distance, Constituent c, TreeView parseView){
    	Constituent rightSibling = c;
    	IQueryable<Constituent> siblings = parseView.where(Queries.isSiblingOf(c));
    	while(distance-- > 0){
    		Iterator<Constituent> rightSiblingIt = siblings.where(Queries.adjacentToAfter(rightSibling)).iterator();
    		if(rightSiblingIt.hasNext())
    			rightSibling = rightSiblingIt.next();
    		else
    			return null;
    	}
    	return rightSibling;
    }
    
    public static String getNotation(Constituent c){
    	if(c == null)
    		return "NULL";
    	String notation = c.getLabel();
    	
    	if(NERlexicalise)
    		notation += " -" + getNamedEntityTag(c);
    	
    	if(POSlexicalise){
			notation += " -";
			IntPair span = c.getSpan();
			TextAnnotation TA = c.getTextAnnotation();
			for (int tokenId = span.getFirst(); tokenId < span.getSecond(); tokenId++)
					notation += " " + WordHelpers.getPOS(TA, tokenId);
	    }
    	
		return notation;
    }
    
    public List<String> getContainingSRLs() {
        List<String> list = new ArrayList<String>();
        TextAnnotation srlTA = (GOLD)? goldTA : TA;
    	PredicateArgumentView pav;
        pav = (PredicateArgumentView)srlTA.getView(ViewNames.SRL_VERB);
        for(Constituent pred : pav.getPredicates()) {
            for (Relation rel : pav.getArguments(pred)) {
                if (rel.getTarget().getEndSpan() > commaPosition && rel.getTarget().getStartSpan() >= commaPosition)
                    list.add(pav.getPredicateLemma(rel.getSource()) + rel.getRelationName());
            }
        }
        pav = (PredicateArgumentView)srlTA.getView(ViewNames.SRL_NOM);
        for(Constituent pred : pav.getPredicates()) {
            for (Relation rel : pav.getArguments(pred)) {
                if (rel.getTarget().getEndSpan() > commaPosition && rel.getTarget().getStartSpan() >= commaPosition)
                    list.add(pav.getPredicateLemma(rel.getSource()) + rel.getRelationName());
            }
        }
        // We don't have gold prepSRL (for now)
        pav = (PredicateArgumentView)TA.getView(ViewNames.SRL_PREP);
        for(Constituent pred : pav.getPredicates()) {
            for (Relation rel : pav.getArguments(pred)) {
                if (rel.getTarget().getEndSpan() > commaPosition && rel.getTarget().getStartSpan() >= commaPosition)
                    list.add(pav.getPredicateLemma(rel.getSource()) + rel.getRelationName());
            }
        }
		return list;
    }

    public static String getNamedEntityTag(Constituent c){
    	TextAnnotation TA = c.getTextAnnotation();
    	List<String> NETags = TA.getView(ViewNames.NER).getLabelsCovering(c);
    	String result = NETags.size()==0? "NULL" : NETags.get(0);
    	for(int i = 1; i<NETags.size(); i++)
    		result += " " + NETags.get(i);
    	return result;
    }
}



