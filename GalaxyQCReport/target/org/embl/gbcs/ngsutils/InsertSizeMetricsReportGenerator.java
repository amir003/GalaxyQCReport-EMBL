package org.embl.gbcs.ngsutils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.embl.cg.utilitytools.utils.FileUtil;
import org.embl.cg.utilitytools.utils.parser.csv.CSVLine;
import org.embl.cg.utilitytools.utils.parser.csv.CSVParser;
import org.embl.cg.utilitytools.utils.parser.csv.InvalidCSVSetUpException;

public class InsertSizeMetricsReportGenerator {
	private static Logger log = Logger.getLogger(InsertSizeMetricsReportGenerator.class);
	private static final String HTML_TABLE_ID = "insertSizeMetricsTable";
	
	//pattern like "Duplication_metrics_<sample>_bowtie2<extrainfo>" with <extrainfo> being optional
	public static Pattern InsertSizeReportDirNamePattern = Pattern.compile("^InsertSize_metrics_(.+)_"+GenerateNGSQCReportAfterGalaxyWF.MAPPERNAME+"(.*)$");
		

	/**
	 * The dir containing all sample reports (also found as dirs)
	 */
	protected File reportDir;
	
	/**
	 * The list of samples to consider, other will be ignored 
	 */
	protected List<String> samples;
	
	/**
	 * empty string means default category
	 */
	protected Set<String> fileCategories = new TreeSet<String>();
	
	/**
	 * The HTML File this class generates 
	 */
	protected File htmlSummaryFile = null;
	
	public InsertSizeMetricsReportGenerator(File reportDir, List<String> samples){
		this.reportDir = reportDir;
		this.samples = samples;
	}

	public void generateReport(File outDir) {
		
		//get list of dirs
		List<File> subdirs = new ArrayList<File>();
		for(File f : reportDir.listFiles()){
			if(f.isDirectory()){
				String spl = sampleNameFromInsertSizeReportDirName(f.getName());
				//System.out.println(f.getName() + "=> spl is "+spl);
				if(isValidSampleDir(spl))
					subdirs.add(f);
			}
		}
		
		//how many "categories" do we have ?
		for (File reportDir : subdirs) {
			Matcher m = InsertSizeReportDirNamePattern.matcher(reportDir.getName());
			m.matches(); //all reportDir do match at this step
			String cat = m.group(2);
			if(cat == null) cat = "";
			fileCategories.add(cat);
		}
		
		PrintWriter htmlpw = null;
		try{
			//writer for html report
			htmlSummaryFile = new File(outDir, "CollectInsertSizeMetricsReport.html");
			htmlpw = new PrintWriter(htmlSummaryFile);
			htmlpw.println("<html><head>");
			htmlpw.println(GenerateNGSQCReportAfterGalaxyWF.getHmtlInclude(1) );
			htmlpw.println("<style>"+GenerateNGSQCReportAfterGalaxyWF.TABLE_CSS+"</style>");
			htmlpw.println("<script>$(document).ready(function() { " +
					"$('.tablesorter').each( function(){$(this).tablesorter()});" +
					" } );</script>" );
			
			
			htmlpw.println("</head><body>");
			
			htmlpw.println("<h1>Insert Size Metrics Report (Paired-end data)</h1>");
			
			//we drive the extraction by cat and sample name
			for (String cat : fileCategories) {
				htmlpw.println("<h2>Insert Size collected on "+(cat.length() > 0 ? cat.replaceFirst("_", "") +" bam files" : "default bam files")+"</h2>");
				
				//use a linked map to preserve sample order
				LinkedHashMap<String, CSVLine> lhm = new LinkedHashMap<String, CSVLine>();
				HashMap<String, File> sample2dir = new HashMap<String, File>();
				HashMap<String, File> sample2htmlReportFile = new HashMap<String, File>();
				
				for (String spl : samples) {
					File htmlreport = null; // the html report file in the sample dir
					File theone = null; // the sample dirname
					boolean unambigousSampleIdentified = true;
					
					//find the dir
					for (File reportDir : subdirs) {
						Matcher m = InsertSizeReportDirNamePattern.matcher(reportDir.getName());
						m.matches(); //all reportDir do match at this step
						String _spl = m.group(1);
						String _c = m.group(2);
						if(_c == null) _c = "";
						//with sample name trimming options, it gets more complicated as _spl can be the untrimmed version
						if(_spl.contains(spl) && _c.equals(cat)){ 
							if(theone!=null)
								unambigousSampleIdentified = false;
							theone = reportDir;
							
							//break; dont break ie to make sure we only match one sample
						}
					}
					
					if(theone == null || !unambigousSampleIdentified){
						if(theone == null) 
							log.warn("Could not find InsertSize Metrics result directory for sample '"+spl+"' in analysis category '"+cat+"'");
						else
							log.error("More than one InsertSize Metrics result directory matches for sample '"+spl+"' in analysis category '"+cat+"'");
						lhm.put(spl, null);
						continue;
					}
					
					//extract info from report file
					File report = new File(theone, "CollectInsertSizeMetrics.metrics.txt");
					htmlreport = theone.listFiles(FileUtil.getFileFilter("InsertSize_metrics", "html"))[0];
					
					sample2dir.put(spl, theone);
					sample2htmlReportFile.put(spl, htmlreport);
					
					
					
					CSVParser p = null;
					try {
						p = new CSVParser(-1, -1, true);
						p.setStoreCommentLines(true);
						p.setStopParsingPattern(Pattern.compile("^## HISTOGRAM.*"));
						
						Iterator<CSVLine> lines = p.iterator(report);
						int n = 0;
						while (lines.hasNext()) {
							CSVLine l = lines.next();
							if(l.isHeaderLine() ){
								if(n==0 && !lhm.containsKey(GenerateNGSQCReportAfterGalaxyWF.SAMPLE_HEADER)){
									lhm.put(GenerateNGSQCReportAfterGalaxyWF.SAMPLE_HEADER, l);
								}
							}
							else{
								lhm.put(spl, l);
								break;
							}
							n++;
						}
					} catch (InvalidCSVSetUpException e) {
						throw new RuntimeException(e);
					} catch (IOException e) {
						throw new RuntimeException(e);
					} finally{
						if(p!=null)
							p.terminate();
					}
				}

				//print 
				PrintWriter pw = null;
				try {
					//prepare file for table
					File summaryResultTable = new File(outDir, "Insert_Size_metrics_Summary"+cat+".txt");
					pw = new PrintWriter(summaryResultTable);
					Map<String, String> parameters = new LinkedHashMap<String, String>();
					parameters.put("Sample List", Arrays.toString(samples.toArray()));
					FileUtil.addGeneratorMetadataAsComments("#", this, parameters, pw);
					
					//add in html
					htmlpw.println("<h3>Result table</h3>");
					htmlpw.println("<div>");
					htmlpw.println("<table id='"+HTML_TABLE_ID+"_"+cat+"' class=\"tablesorter\">");
					int n = 0;
					for (Entry<String, CSVLine> e : lhm.entrySet()) {
						pw.println(e.getKey()+"\t"+e.getValue().merge("\t"));
						
						if(n==0){
							htmlpw.println("<thead>");
							htmlpw.println("<tr>");
							htmlpw.println("<th>"+e.getKey()+"</th><th>"+e.getValue().merge("</th><th>").replaceAll("_", " ")+"</th><th>Histogram(PDF)</th><th>Individual Report Link</th>");
							htmlpw.println("</tr>");
							htmlpw.println("</thead><tbody>");
						}
						else{
							String lnk = "./" +sample2dir.get(e.getKey()).getName()+ "/InsertSizeHist.pdf" ;
							String lnk2 = "./" +sample2dir.get(e.getKey()).getName()+ "/"+ sample2htmlReportFile.get(e.getKey()).getName() ;
							htmlpw.println("<tr>");
							htmlpw.println(
									"<td>"+e.getKey()+"</td><td>"+e.getValue().merge("</td><td>")+"</td>"+
									"<td><a href=\"" +lnk+ "\" target=\"_blank\">Open PDF</td>" +
									"<td><a href=\"" +lnk2+ "\" target=\"_blank\">Open Report</td>"
									);
							htmlpw.println("</tr>");
						}
						
						n++;
					}
					htmlpw.println("</tbody></thead>");
					htmlpw.println("</table>");
					htmlpw.println("</div>");
				} catch (IOException e) {
					throw new RuntimeException(e);
				} finally{
					if(pw!=null)
						pw.close();
				}
			}
			
			
			htmlpw.println("</body></html>");
			
		}catch (IOException e) {
			throw new RuntimeException(e);
		}finally{
			if(htmlpw!=null)
				htmlpw.close();
		}
		System.out.println("Processing Insert Size Metrics Reports...DONE");
	}
	
	
	/**
	 * @param name the dir name 
	 * @return the sample name if dir name matches the expected dir name pattern or null
	 */
	public static String sampleNameFromInsertSizeReportDirName(String name) {
		Matcher m = InsertSizeReportDirNamePattern.matcher(name);
		String s = null;
		if(m.matches()){
			return m.group(1);
		}
		return null;
	}
	
	/**
	 * @return the htmlSummaryFile
	 */
	public File getHtmlSummaryFile() {
		return htmlSummaryFile;
	}

	/**
	 * @param htmlSummaryFile the htmlSummaryFile to set
	 */
	public void setHtmlSummaryFile(File htmlSummaryFile) {
		this.htmlSummaryFile = htmlSummaryFile;
	}
	private boolean isValidSampleDir(String spl) {
		for (String s : samples) {
			if(spl.contains(s))
				return true;
		}
		return false;
	}
}
