package org.embl.gbcs.ngsutils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.embl.cg.utilitytools.utils.FileUtil;
import org.embl.cg.utilitytools.utils.parser.csv.CSVLine;
import org.embl.cg.utilitytools.utils.parser.csv.CSVParser;
import org.embl.cg.utilitytools.utils.parser.csv.InvalidCSVSetUpException;

public class MultiFastQCReportGenerator {

	private static Logger log = Logger.getLogger(MultiFastQCReportGenerator.class);
	 
	
	private static final String HTML_TABLE_ID = "FASTQCreportTableMetricsTable";
	
	//
	public static Pattern FASTQCReportDirNamePattern = Pattern.compile("^FastQCreport_(.+)_"+GenerateNGSQCReportAfterGalaxyWF.MAPPERNAME+"(.*)$");
		

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
	
	public MultiFastQCReportGenerator(File reportDir, List<String> samples){
		this.reportDir = reportDir;
		this.samples = samples;
	}

	public void generateReport(File outDir) {
		
		//get list of dirs
		List<File> subdirs = new ArrayList<File>();
		for(File f : reportDir.listFiles()){
			if(f.isDirectory()){
				String spl = sampleNameFromReadDuplicationReportDirName(f.getName());
				//System.out.println(f.getName() + "=> spl is "+spl);
				if(isValidSampleDir(spl))
					subdirs.add(f);
			}
		}
		
		//how many "categories" do we have ?
		for (File reportDir : subdirs) {
			Matcher m = FASTQCReportDirNamePattern.matcher(reportDir.getName());
			m.matches(); //all reportDir do match at this step
			String cat = m.group(2);
			if(cat == null) cat = "";
			log.info("FASTQC report => cat : "+cat);
			fileCategories.add(cat);
		}
		
		PrintWriter htmlpw = null;
		try{
			//writer for html report
			htmlSummaryFile = new File(outDir, "MultiFASTQCSummaryReport.html");
			htmlpw = new PrintWriter(htmlSummaryFile);
			
			htmlpw.println("<html><head>");
			htmlpw.println(GenerateNGSQCReportAfterGalaxyWF.getHmtlInclude(1) );
			htmlpw.println("<style>"+GenerateNGSQCReportAfterGalaxyWF.TABLE_CSS+"</style>");
			
			htmlpw.println("<script>$(document).ready(function() { " +
					"$('.tablesorter').each( function(){$(this).tablesorter()});" +
					" } );</script>" );
			
			
			htmlpw.println("</head><body>");
			
			htmlpw.println("<h1>FASTQC Reports Overview Report</h1>");
			
			//we drive the extraction by cat and sample name
			for (String cat : fileCategories) {
				log.info("Fastqc report , processing cat : "+cat);
				htmlpw.println("<h2>FASTQC collected on "+(cat.length() > 0 ? cat.replaceFirst("_", "") +" files" : "default files")+"</h2>");
				
				//use a linked map to preserve sample order
				LinkedHashMap<String, Map<String, String>> lhm = new LinkedHashMap<String, Map<String, String>>();
				HashMap<String, File> sample2dir = new HashMap<String, File>();
				HashMap<String, File> sample2htmlReportFile = new HashMap<String, File>();
				
				for (String spl : samples) {
					File htmlreport = null; // the html report file in the sample dir
					File theone = null; // the sample dir
					boolean unambigousSampleIdentified = true;
					//find the dir
					for (File reportDir : subdirs) {
						Matcher m = FASTQCReportDirNamePattern.matcher(reportDir.getName());
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
							log.warn("Could not find FASTQC result directory for sample '"+spl+"' in analysis category '"+cat+"'");
						else
							log.error("More than one FASTQC result directory matches for sample '"+spl+"' in analysis category '"+cat+"'");
						sample2dir.put(spl, null);
						sample2htmlReportFile.put(spl, null);
						lhm.put(spl, null);
						continue;
					}
					
					Map<String, String> header2value = new LinkedHashMap<String, String>(); //important !
					
					
					//extract info from report file
					File report = new File(theone, "summary.txt");
					htmlreport = theone.listFiles(FileUtil.getFileFilter("FastQCreport_", "html"))[0];
					CSVParser p = null;
					
					try {
						p = new CSVParser(3, 3, false);
						Iterator<CSVLine> lines = p.iterator(report);
						while (lines.hasNext()) {
							CSVLine l = lines.next();
							header2value.put(l.getValue(1), l.getValue(0));
						}
					} catch (InvalidCSVSetUpException e) {
						throw new RuntimeException(e);
					} catch (IOException e) {
						throw new RuntimeException(e);
					} finally{
						if(p!=null)
							p.terminate();
					}
					sample2dir.put(spl, theone);
					sample2htmlReportFile.put(spl, htmlreport);
					lhm.put(spl, header2value);
				}

				//add in html
				htmlpw.println("<h3>Result table</h3>");
				htmlpw.println("<div>");
				htmlpw.println("<table id='"+HTML_TABLE_ID+"_"+cat+"' class=\"tablesorter\">");
				int n = 0;
				int colnum = 0; //num of columns from the parsed fastqc file 
				for (Entry<String, Map<String, String>> e : lhm.entrySet()) {
					
					if(n==0){
						htmlpw.println("<thead>");
						htmlpw.println("<tr>");
						htmlpw.println("<th>Sample</th>");
						for (String h : e.getValue().keySet()) {
							htmlpw.println("<th>"+h+"</th>");
							colnum++;
						}
						htmlpw.println("<th>FASTQC Report Link</th>");
						htmlpw.println("</tr>");
						htmlpw.println("</thead><tbody>");
					}
					
					htmlpw.println("<tr>");
					//print values
					htmlpw.println("<td>"+e.getKey()+"</td>");
					if(e.getValue() == null){
						for (int i = 0; i < colnum; i++) {
							htmlpw.println("<td> NA </td>");
						}
						htmlpw.println("<td> Report not found </td>");
					}else{
						for (String v : e.getValue().values()) {
							String color = "green";
							if(!v.equalsIgnoreCase("PASS")){
								color = v.equalsIgnoreCase("WARN") ? "orange" : "red";
							}
							htmlpw.println("<td style=\"background-color:"+color+"\">"+v+"</td>");
						}
						String lnk = "./" +sample2dir.get(e.getKey()).getName()+ "/"+ sample2htmlReportFile.get(e.getKey()).getName() ;
						htmlpw.println("<td><a href=\"" +lnk+ "\" target=\"_blank\">Open Report</td>" );
					}
					//end of row	
					htmlpw.println("</tr>");

					n++;
				}
				
				htmlpw.println("</tbody></thead>");
				htmlpw.println("</table>");
				htmlpw.println("</div>");
				
			}
			
			
			htmlpw.println("</body></html>");
			
		}catch (IOException e) {
			throw new RuntimeException(e);
		}finally{
			if(htmlpw!=null)
				htmlpw.close();
		}
		log.debug("Processing Insert Size Metrics Reports...DONE");
	}
	
	
	private boolean isValidSampleDir(String spl) {
		for (String s : samples) {
			if(spl.contains(s))
				return true;
		}
		return false;
	}

	/**
	 * @param name the dir name 
	 * @return the sample name if dir name matches the expected dir name pattern or null
	 */
	public static String sampleNameFromReadDuplicationReportDirName(String name) {
		Matcher m = FASTQCReportDirNamePattern.matcher(name);	
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
	
}
