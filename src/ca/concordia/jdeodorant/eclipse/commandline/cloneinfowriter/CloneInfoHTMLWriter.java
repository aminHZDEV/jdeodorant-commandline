package ca.concordia.jdeodorant.eclipse.commandline.cloneinfowriter;

import gr.uom.java.ast.decomposition.cfg.mapping.CloneStructureNode;
import gr.uom.java.ast.decomposition.cfg.mapping.IdBasedGap;
import gr.uom.java.ast.decomposition.cfg.mapping.MappingState;
import gr.uom.java.ast.decomposition.cfg.mapping.NodeMapping;
import gr.uom.java.ast.decomposition.cfg.mapping.PDGElseGap;
import gr.uom.java.ast.decomposition.cfg.mapping.PDGElseMapping;
import gr.uom.java.ast.decomposition.cfg.mapping.PDGNodeGap;
import gr.uom.java.ast.decomposition.cfg.mapping.PDGNodeMapping;
import gr.uom.java.ast.decomposition.cfg.mapping.PDGRegionSubTreeMapper;
import gr.uom.java.ast.decomposition.cfg.mapping.precondition.NormalStyler;
import gr.uom.java.ast.decomposition.cfg.mapping.precondition.PreconditionViolation;
import gr.uom.java.ast.decomposition.cfg.mapping.precondition.Suggestion;
import gr.uom.java.ast.decomposition.matching.ASTNodeDifference;
import gr.uom.java.ast.decomposition.matching.Difference;
import gr.uom.java.jdeodorant.refactoring.views.CloneDiffSide;
import gr.uom.java.jdeodorant.refactoring.views.StyledStringStyler;
import gr.uom.java.jdeodorant.refactoring.views.StyledStringVisitor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;

import ca.concordia.jdeodorant.eclipse.commandline.Activator;
import ca.concordia.jdeodorant.eclipse.commandline.diff.TextDiff;
import ca.concordia.jdeodorant.eclipse.commandline.diff.TextDiff.Diff;
import ca.concordia.jdeodorant.eclipse.commandline.diff.TextDiff.Operation;
import ca.concordia.jdeodorant.eclipse.commandline.parsers.CloneToolParser;

public class CloneInfoHTMLWriter extends CloneInfoWriter {
	
	private final static String START_OF_REPEATING_PART = "<!-- {@START} -->";
	private final static String END_OF_REPEATING_PART = "<!-- {@END} -->";
	private final static String TEMPLATE_PATH = Activator.getPluginPath() + "/res/";
	private final static String TEMPLATE_NAME = "template.htm";
	private final static String TEMPLATE_EXTRA_FILES_FOLDER = "template.files";
	private final String templateText;
	public static final String PATH_TO_HTML_REPORTS = "html.reports";
	
	public CloneInfoHTMLWriter(String outputFolder, String projectName, String fileNamesPrefix) {
		
		super(outputFolder + "/" + PATH_TO_HTML_REPORTS, projectName, fileNamesPrefix);
		
		copyExtraFiles(this.outputFileSaveFolder);
		try {
			templateText = readFile(TEMPLATE_PATH + TEMPLATE_NAME, Charset.defaultCharset());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void writeCloneInfo(ClonePairInfo pairInfo) {

		String diffTableHTML = getDiffTableHTML(pairInfo.getSourceCodeFirst(), pairInfo.getSourceCodeSecond());
		
		int refactorableCount = 0;
		for (PDGSubTreeMapperInfo mapperInfo : pairInfo.getPDFSubTreeMappersInfoList())
			if (mapperInfo.isRefactorable())
				refactorableCount++;
		
		String templateFileText = templateText;
		String html = templateFileText.substring(0, templateFileText.indexOf(START_OF_REPEATING_PART))
				.replace("@DiffTableRows", diffTableHTML)
				.replace("@FirstCode", escapeHTML(pairInfo.getSourceCodeFirst()))
				.replace("@SecondCode", escapeHTML(pairInfo.getSourceCodeSecond()))
				.replace("@FirstRealCodeFile", CloneToolParser.CODE_FRAGMENTS_TXT_FILES_FOLDER_NAME + "/" + pairInfo.getCloneGroupID() + "-" + pairInfo.getCloneFragment1ID() + ".txt")
				.replace("@SecondRealCodeFile", CloneToolParser.CODE_FRAGMENTS_TXT_FILES_FOLDER_NAME + "/" + pairInfo.getCloneGroupID() + "-" + pairInfo.getCloneFragment1ID() + ".txt")
				.replace("@Title", pairInfo.getCloneGroupID() + "-" + pairInfo.getClonePairID())
				.replace("@FilePath1", pairInfo.getContainingFileFirst())
				.replace("@MethodName1", pairInfo.getFirstMethodSignature())
				.replace("@FilePath2", pairInfo.getContainingFileSecond())
				.replace("@MethodName2", pairInfo.getSecondMethodSignature())
				.replace("@ASTNodes1", String.valueOf(pairInfo.getNumberOfCloneStatementsInFirstCodeFragment()))
				.replace("@ASTNodes2", String.valueOf(pairInfo.getNumberOfCloneStatementsInSecondCodeFragment()))
				.replace("@OpportunitiesCount",String.valueOf(pairInfo.getPDFSubTreeMappersInfoList().size()))
				.replace("@RefactorableOpportunitiesCount", String.valueOf(refactorableCount))
				.replace("@NonRefactorableOpportunitiesCount", String.valueOf(pairInfo.getPDFSubTreeMappersInfoList().size() - refactorableCount))
				.replace("@BottomUpMatchingTime", String.format("%.1f", pairInfo.getSubtreeMatchingWallNanoTime() / 1000000F))
				.replace("@NodeComparisons", String.valueOf(pairInfo.getNumberOfNodeComparisons()))
				.replace("@ClonesLocation", pairInfo.getLocation() != null ? pairInfo.getLocation().getDescription() : "");
		
		html +=  templateFileText.substring(templateFileText.indexOf(END_OF_REPEATING_PART) + END_OF_REPEATING_PART.length());
		
		String repeatingPart = templateFileText.substring(
				templateFileText.indexOf(START_OF_REPEATING_PART) + START_OF_REPEATING_PART.length(), 
				templateFileText.indexOf(END_OF_REPEATING_PART));
		
		String refactorableHTML = "", notrefactorableHTML = "";
		for (PDGSubTreeMapperInfo mapperInfo : pairInfo.getPDFSubTreeMappersInfoList()) {
			
			PDGRegionSubTreeMapper mapper = mapperInfo.getMapper();
			MappingState maximumStateWithMinimumDifferences = mapper.getMaximumStateWithMinimumDifferences();
			
			List<CloneStructureNode> cloneMappingNodes = new ArrayList<>();
			
			String section = repeatingPart.replace(START_OF_REPEATING_PART, "")
					.replace("@MappedCount",  maximumStateWithMinimumDifferences == null ? "0" : String.valueOf(maximumStateWithMinimumDifferences.getNodeMappingSize()))
					.replace("@MappedTable", getMappedTableRows(mapper.getCloneStructureRoot(), cloneMappingNodes))      
					.replace("@PreconditionViolationsCount", String.valueOf(mapper.getPreconditionViolations().size()))
					.replace("@PreconditionViolations", getPreconditionViolationsHTML(mapperInfo))
					.replace("@MappingTime", String.format("%.1f", mapperInfo.getWallNanoTimeElapsedForMapping() / 1000000F))
					.replace("@UnmappedCount1", String.valueOf(mapper.getRemainingNodesG1().size()))
					.replace("@UnmappedCount2", String.valueOf(mapper.getRemainingNodesG2().size()))
					.replace("@CloneType", mapper.getCloneType().toString());                                   
			
			if (mapperInfo.isRefactorable())
				refactorableHTML += section.replace("@RefactorableTitle", "<span class=\"refactorable\">{Refactorable}</span>");
			else 
				notrefactorableHTML += section.replace("@RefactorableTitle", "<span class=\"nonrefactorable\">{Non-refactorable}</span>");
		}
		
		html = html.replace("@Refactorable", refactorableHTML).replace("@NotRefactorable", notrefactorableHTML);
		

		try {					
			BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputFileSaveFolder + pairInfo.getCloneGroupID() + "-" + pairInfo.getClonePairID() + ".htm")));
			writer.write(html);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String escapeHTML(String html) {
		return html.replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Copy template extra files to the output folder
	 * If the folder is exists, its contents will be overridden. 
	 */
	public static void copyExtraFiles(String outputFolder) {
		final String extraFilesFolderPath = outputFolder + TEMPLATE_EXTRA_FILES_FOLDER + "/";
		File extraFilesTargetFolder = new File(extraFilesFolderPath);
		//if (!extraFilesTargetFolder.exists()) {
			
			extraFilesTargetFolder.mkdir();
			
			File[] list = new File(TEMPLATE_PATH + TEMPLATE_EXTRA_FILES_FOLDER).listFiles();
			for (File fileInside : list) {
				try {
					Files.copy(fileInside.getAbsoluteFile().toPath(), Paths.get(extraFilesFolderPath + fileInside.getName()), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		//}
	}

//	private String getPreconditionViolationsHTML(List<CloneStructureNode> nodes) {
//		
//		StringBuilder builder = new StringBuilder();
//		
//		int i = 0;
//		for (CloneStructureNode node : nodes) {
//			if (node.getMapping() != null && node.getMapping().getPreconditionViolations().size() > 0)
//				for (PreconditionViolation violation : node.getMapping().getPreconditionViolations())
//					builder.append(String.format("<tr><td class=\"id\">%s</td><td>%s</td></tr>", ++i, getHTMLFromStyledString(violation.getStyledViolation())));
//		}
//		
//		return builder.toString();
//			
//	}
	
	private String getPreconditionViolationsHTML(PDGSubTreeMapperInfo mapperInfo) {
		
		StringBuilder builder = new StringBuilder();
		
		int i = 0;
		for (PreconditionViolation violation : mapperInfo.getMapper().getPreconditionViolations()) {
			builder.append(String.format("<tr><td class=\"id\">%s</td><td>%s</td></tr>", ++i, getHTMLFromStyledString(violation.getStyledViolation())));
		}
		
		return builder.toString();
			
	}
	
	/**
	 * Returns the HTML code of the mapped statements, 
	 * also it populates the returnedNodes with the CloneStructureNode's in the 
	 * same order as it is shown in the returned HTML table rows  
	 * @param root
	 * @param returnedNodes
	 * @return
	 */
	private String getMappedTableRows(CloneStructureNode root, List<CloneStructureNode> returnedNodes) {
		StringBuilder builder = new StringBuilder();
		if (root != null)
			recursiveGetMappedTableRows(builder, root, 0, returnedNodes);
		return builder.toString();
	}
	
	private void recursiveGetMappedTableRows(StringBuilder builder, CloneStructureNode root, int numberOfTabs, List<CloneStructureNode> returnedNodes) {
		
		Set<CloneStructureNode> children = root.getChildren();
		
		if (children.size() == 0)
			return;
		
		int parentID = returnedNodes.size() - 1;
		
		for (CloneStructureNode node : children) {
			
			returnedNodes.add(node);
			
			String leftStatementHTML = getStatementHTML(node, CloneDiffSide.LEFT, numberOfTabs, returnedNodes.size() - 1);
			String rightStatementHTML = getStatementHTML(node, CloneDiffSide.RIGHT, numberOfTabs, returnedNodes.size() - 1);
			
			builder.append(String.format("<tr class=\"child ID%s\">%s<td class=\"mappedimage\">%s</td>%s</tr>", parentID, leftStatementHTML, getInformationDialogHTML(node), rightStatementHTML));
						
			numberOfTabs++;
			recursiveGetMappedTableRows(builder, node, numberOfTabs, returnedNodes);
			numberOfTabs--;
		}
	}

	private String getStatementHTML(CloneStructureNode node, CloneDiffSide position, int numberOfTabs, int parentNodeID) {
		
		int id = 0;
		if (node.getMapping() != null) {
			if (position == CloneDiffSide.LEFT && node.getMapping().getNodeG1() != null)
				id = node.getMapping().getNodeG1().getId();
			else if (position == CloneDiffSide.RIGHT && node.getMapping().getNodeG2() != null)
				id = node.getMapping().getNodeG2().getId();
		}
		
		StringBuilder statementHTML = new StringBuilder();
		
		// Get an Eclipse StyleString, then make the HTML string based on that
		StyledString styledString;
		
		String className = "";
		
		if(node.getMapping() instanceof PDGNodeMapping) {
			styledString = generateStyledString(node, position);
			if(node.getMapping().isAdvancedMatch()) {
				className = "advancedmatch";
			}
		}
		else if(node.getMapping() instanceof PDGElseMapping) {
			styledString = new StyledString();
			styledString.append("else", new StyledStringStyler(StyledStringVisitor.initializeKeywordStyle()));
		}
		else if(node.getMapping() instanceof PDGNodeGap) {
			styledString = generateStyledStringForGap(node, position);
			if ((position == CloneDiffSide.LEFT && node.getMapping().getNodeG1() != null) ||
					(position == CloneDiffSide.RIGHT && node.getMapping().getNodeG2() != null)) {
				className = node.getMapping().isAdvancedMatch() ? "advancedmatch" : "unmatched";
				//cell.setBackground(new Color(null, 255, 156, 156));
			}
			else {
				className = node.getMapping().isAdvancedMatch() ? "advancedmatchgapped" : "gapped";
			}
		}
		else if(node.getMapping() instanceof PDGElseGap) {
			styledString = generateStyledStringForElseGap((PDGElseGap)node.getMapping(), position);
			if ((position == CloneDiffSide.LEFT && ((PDGElseGap)node.getMapping()).getId1() != 0) ||
					(position == CloneDiffSide.RIGHT && ((PDGElseGap)node.getMapping()).getId2() != 0)) {
				className = node.getMapping().isAdvancedMatch() ? "advancedmatch" : "unmatched";
				//cell.setBackground(new Color(null, 255, 156, 156));
			}
			else {
				className = node.getMapping().isAdvancedMatch() ? "advancedmatchgapped" : "gapped";
			}
		}
		else {
			styledString = new StyledString();
			styledString.append("Root", null);
		}
		
		
		statementHTML.append("<pre class=\"codeview\">");	
		if (node.getChildren().size() > 0) {
			boolean showMarker = true;
			if (node.getMapping() instanceof IdBasedGap) {
				IdBasedGap mapping = (IdBasedGap)node.getMapping();
				if ((position == CloneDiffSide.LEFT && mapping.getId1() == 0) ||
					(position == CloneDiffSide.RIGHT && mapping.getId2() == 0))
					showMarker = false;
			}
			statementHTML.append(String.format("<div class=\"expand ID%s\"%s></div>", parentNodeID, showMarker ? "" : " style=\"display: none\""));
		}
		statementHTML.append(getHTMLFromStyledString(styledString));
		statementHTML.append("</pre>");
		
		return String.format("<td class=\"id %s\">%s</td><td class=\"codeline %s %s\">%s</td>", 
				className, id > 0 ? id : "", position, className, String.format(getTabs(numberOfTabs), statementHTML.toString()));
	}

	private String getInformationDialogHTML(CloneStructureNode node) {
		StringBuilder builder = new StringBuilder();

		NodeMapping mapping = node.getMapping();

		if (mapping.getNodeDifferences().size() > 0 || mapping.getPreconditionViolations().size() > 0) {

			String statementRow = "<tr><td><div class=\"nodeid\">%s</div></td><td><div class=\"statement\">%s</div></td></tr>";
			builder.append("<div class=\"informationdialog\"><table class=\"statements\">");
			if (mapping.getNodeG1() != null)
				builder.append(String.format(statementRow, mapping.getNodeG1().getId(), getHTMLFromStyledString(generateStyledString(node, CloneDiffSide.LEFT))));
			if (mapping.getNodeG2() != null)
				builder.append(String.format(statementRow, mapping.getNodeG2().getId(), getHTMLFromStyledString(generateStyledString(node, CloneDiffSide.RIGHT))));
			builder.append("</table>");

			if (mapping.getNodeDifferences().size() > 0) {
				builder.append("<table class=\"differences\"><caption>Differences</caption>");
				builder.append("<thead><tr><th>Expression1</th><th>Expression2</th><th>Difference</th></tr></thead>");
				builder.append("<tbody>");
				String differenceRow = "<tr><td>%s</td><td>%s</td><td>%s</td></tr>";
				for (ASTNodeDifference difference : mapping.getNodeDifferences()) {
					for (Difference diff : difference.getDifferences())
						builder.append(String.format(differenceRow, diff.getFirstValue(), diff.getSecondValue(), diff.getType().name()));
				}
				builder.append("</tbody></table>");
			}
			
			if (mapping.getPreconditionViolations().size() > 0) {
				builder.append("<table class=\"preconditionviolations\"><caption>Preondition Violations</caption>");
				//builder.append("<thead><tr><th>Expression1</th><th>Expression2</th><th>Difference Type</th><th>Difference</th></tr></thead>");
				builder.append("<tbody>");
				String violationRow = "<tr><td>%s%s</td></tr>";
				for (PreconditionViolation violation : mapping.getPreconditionViolations()) {
					String suggestionsHTML = "";
					if (violation.getSuggestions().size() > 0) {
						StringBuilder suggestionsListHTML = new StringBuilder();
						for (Suggestion suggestion : violation.getSuggestions()) {
							suggestionsListHTML.append("<li>");
							suggestionsListHTML.append(suggestion.getSuggestion());
							suggestionsListHTML.append("</li>");
						}
						suggestionsHTML = String.format("<ul class=\"suggestions\">%s</ul>", suggestionsListHTML);
					}
						
					builder.append(String.format(violationRow, getHTMLFromStyledString(violation.getStyledViolation()), suggestionsHTML));
				}
				builder.append("</tbody></table>");
			}

			builder.append("</div>");

		}
		
		return builder.toString();
	}

	/**
	 * Returns HTML code from StyledString
	 * @param styledString
	 * @return
	 */
	public static String getHTMLFromStyledString(StyledString styledString) {
		StyleRange[] ranges = styledString.getStyleRanges();
		StringBuilder toReturn = new StringBuilder();
		
		for (StyleRange range : ranges) {
			String style = "";
			if (range.foreground != null)
				style = String.format("color: rgb(%s, %s, %s);",
											range.foreground.getRed(),
											range.foreground.getGreen(),
											range.foreground.getBlue());
			
			if (range.background != null)
				style += String.format("background: rgb(%s, %s, %s);",
											range.background.getRed(),
											range.background.getGreen(),
											range.background.getBlue());
			
			if (range.fontStyle == SWT.BOLD)
				style += "font-weight: bold;";
			if (range.fontStyle == SWT.ITALIC)
				style += "font-style: italic;";
			
			if (range.font != null && range.font.getFontData().length > 0)
				style += String.format("font-family: '%s'", range.font.getFontData()[0].getName());
			
			toReturn.append(String.format("<span style=\"%s\">%s</span>", style, escapeHTML(styledString.getString().substring(range.start, range.start + range.length))));
		}

		return  toReturn.toString();
	}
	
	private String getTabs(int numberOfTabs) {
		String toReturn = "%s";
		for (int i = 0; i < numberOfTabs; i++)
				toReturn = String.format(toReturn, "<div class=\"tree\">%s</div>");
		return toReturn;
	}

	private StyledString generateStyledString(CloneStructureNode theNode, CloneDiffSide diffSide) {
		ASTNode astStatement = null;
		StyledString styledString;
		StyledStringVisitor leafVisitor = new StyledStringVisitor(theNode, diffSide);
		if (diffSide == CloneDiffSide.LEFT){
			astStatement = theNode.getMapping().getNodeG1().getASTStatement();
		}
		else if (diffSide == CloneDiffSide.RIGHT){
			astStatement = theNode.getMapping().getNodeG2().getASTStatement();
		}
		astStatement.accept(leafVisitor);
		styledString = leafVisitor.getStyledString();
		return styledString;
	}
	
	private StyledString generateStyledStringForGap(CloneStructureNode theNode, CloneDiffSide diffSide) {
		StyledString styledString = null;
		if (diffSide == CloneDiffSide.LEFT) {
			if(theNode.getMapping().getNodeG1() != null) {
				styledString = generateStyledString(theNode, diffSide);
			}
			//This creates a blank Label containing only spaces, to match the length of the corresponding Gap statement. 
			else{
				String correspondingStatement = theNode.getMapping().getNodeG2().toString();
				StringBuilder str = new StringBuilder();
				for (int i = 0; i < correspondingStatement.length(); i++){
					str.append("  ");
				}
				styledString = new StyledString(str.toString(), new NormalStyler());
			}
		}
		else if (diffSide == CloneDiffSide.RIGHT) {
			if(theNode.getMapping().getNodeG2() != null) {
				styledString = generateStyledString(theNode, diffSide);
			}
			//This creates a blank Label containing only spaces, to match the length of the corresponding Gap statement. 
			else{
				String correspondingStatement = theNode.getMapping().getNodeG1().toString();
				StringBuilder str = new StringBuilder();
				for (int i = 0; i < correspondingStatement.length(); i++){
					str.append("  ");
				}
				styledString = new StyledString(str.toString(), new NormalStyler());
			}
		}
		return styledString;
	}
	
	private StyledString generateStyledStringForElseGap(PDGElseGap elseGap, CloneDiffSide diffSide) {
		StyledString styledString = null;
		if (diffSide == CloneDiffSide.LEFT) {
			if(elseGap.getId1() != 0) {
				styledString = new StyledString();
				styledString.append("else", new StyledStringStyler(StyledStringVisitor.initializeKeywordStyle()));
			}
			//This creates a blank Label containing only spaces, to match the length of the corresponding Gap statement. 
			else{
				String correspondingStatement = "else";
				StringBuilder str = new StringBuilder();
				for (int i = 0; i < correspondingStatement.length(); i++){
					str.append("  ");
				}
				styledString = new StyledString(str.toString(), new NormalStyler());
			}
		}
		else if (diffSide == CloneDiffSide.RIGHT) {
			if(elseGap.getId2() != 0) {
				styledString = new StyledString();
				styledString.append("else", new StyledStringStyler(StyledStringVisitor.initializeKeywordStyle()));
			}
			//This creates a blank Label containing only spaces, to match the length of the corresponding Gap statement. 
			else{
				String correspondingStatement = "else";
				StringBuilder str = new StringBuilder();
				for (int i = 0; i < correspondingStatement.length(); i++){
					str.append("  ");
				}
				styledString = new StyledString(str.toString(), new NormalStyler());
			}
		}
		return styledString;
	}

	private String getDiffTableHTML(String code1, String code2) {
		
		TextDiff td = new TextDiff();
		LinkedList<Diff> diffs = td.diff_main(escapeHTML(code1), escapeHTML(code2), false);
		td.diff_cleanupSemantic(diffs);
		
		
		String firstDiff = "", secondDiff = "";

//		for (Diff ca.concordia.jdeodorant.eclipse.commandline.diff : diffs) {
//			System.out.println(ca.concordia.jdeodorant.eclipse.commandline.diff);
//		}
		for (Diff diff : diffs) {
			
			String text = diff.text.replaceAll("\r\n", "\n");
			
			String html = "<span class=\"@class\">";
			html += text.replace("\n", "</span>" + "\n" + "<span class=\"@class\">");
			html +=  "</span>";
			
			html = html.replaceAll("<span class=\"(.*)\"></span>", "");
			
			int numberOfLineBreaks = diff.text.length() - diff.text.replace("\n", "").length();
			
			if (diff.operation == Operation.EQUAL) {
				html = html.replace("@class", "normal");
				firstDiff += html;
				secondDiff += html;
			} else if (diff.operation == Operation.DELETE) {
				firstDiff += html.replace("@class", "deleted");
				for (int i = 0; i < numberOfLineBreaks; i++)
					secondDiff += "\n";
			} else if (diff.operation == Operation.INSERT) {
				secondDiff += html.replace("@class", "inserted");	
				for (int i = 0; i < numberOfLineBreaks; i++)
					firstDiff += "\n";
				//(!"".equals(firstDiff) ? "\n" : "")
			}			
		}
				
		String[] linesFirst = (firstDiff).split("\n");
		String[] linesSecond = (secondDiff).split("\n");
		
		String html = "";
		int firstLine = 0, secondLine = 0;
		for (int i = 0; i < Math.max(linesFirst.length, linesSecond.length); i++) {
			
			//if (linesFirst[i].trim().equals("") || linesSecond[i].trim().equals(""))
			//	continue;
			
			html += "<tr class=\"coderow\">";
			
			String className = "";
			if (i >= linesFirst.length || "".equals(linesFirst[i]) || "<span class=\"normal\"></span>".equals(linesFirst[i])) {
				className = "gap";
			} else if (linesFirst[i].contains("class=\"inserted\"") || linesFirst[i].contains("class=\"deleted\"")) {
				className = "changed";
			}
			
			String className2 = "";
			if (i >= linesSecond.length || "".equals(linesSecond[i]) || "<span class=\"normal\"></span>".equals(linesSecond[i])) {
				className2 = "gap";
			} else if (linesSecond[i].contains("class=\"inserted\"") || linesSecond[i].contains("class=\"deleted\"")) {
				className2 = "changed";
			} 
			
			if (className.equals("gap") && className.equals(className2))
				continue;
			
			html += String.format("<td class=\"lines %s\">", className);
			
			if (!"gap".equals(className))
				html += (++firstLine);
			
			html += "</td>";
			html += String.format("<td class=\"%s\">", className);
			if (i < linesFirst.length) {
				html += "<pre>" + linesFirst[i];
				if (!"gap".equals(className) && i < linesFirst.length - 1)
					html += "<span class=\"carr\">&crarr;</span>";
				html += "</pre>";
			}
			html += "</td>";
			
			html += "<td class=\"separator\"></td>";
			
			html += String.format("<td class=\"lines %s\">", className2);
			
			if (!"gap".equals(className2))
				html += (++secondLine);
			
			html += "</td>";
			html += String.format("<td class=\"%s\"><pre>", className2);
			if (i < linesSecond.length) {
				html += "<pre>" + linesSecond[i];
				if (!"gap".equals(className2) && i < linesSecond.length - 1)
					html += "<span class=\"carr\">&crarr;</span>";
				html += "</pre>";
			}
			html += "</pre></td>";
			html += "</tr>" + System.lineSeparator();
		}
		return html;
	}
	
	private String readFile(String path, Charset encoding) throws IOException 
	{
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	}

	@Override
	public void closeMedia(boolean append) {
		// Dummy
	}

	
	
}
