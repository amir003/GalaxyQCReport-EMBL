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

public class ReadDuplicationMetricsReportGenerator {
	private static Logger log = Logger.getLogger(ReadDuplicationMetricsReportGenerator.class);
	
	private static final String HTML_TABLE_ID = "readDupsMetricsTable";
	
	//pattern like "Duplication_metrics_<sample>_bowtie2<extrainfo>" with <extrainfo> being optional
	public static Pattern ReadDuplicationReportDirNamePattern = Pattern.compile("^Duplication_metrics_(.+)_"+GenerateNGSQCReportAfterGalaxyWF.MAPPERNAME+"(.*)$");
		
	
	public static final String HEADER_SAMPLE = "Sample";
	
	public static final String HEADER_LIBRARY = "LIBRARY";
	public static final String HEADER_UNPAIRED_READS_EXAMINED = "UNPAIRED_READS_EXAMINED";
	public static final String HEADER_READ_PAIRS_EXAMINED = "READ_PAIRS_EXAMINED";
	public static final String HEADER_UNMAPPED_READS = "UNMAPPED_READS";
	public static final String HEADER_UNPAIRED_READ_DUPLICATES = "UNPAIRED_READ_DUPLICATES";
	public static final String HEADER_READ_PAIR_DUPLICATES = "READ_PAIR_DUPLICATES";
	public static final String HEADER_READ_PAIR_OPTICAL_DUPLICATES = "READ_PAIR_OPTICAL_DUPLICATES";
	public static final String HEADER_PERCENT_DUPLICATION = "PERCENT_DUPLICATION";
	public static final String HEADER_ESTIMATED_LIBRARY_SIZE = "ESTIMATED_LIBRARY_SIZE";
	

	public static Set<String> drawable_headers = new TreeSet<String>();
	static{
		drawable_headers.add(HEADER_UNPAIRED_READS_EXAMINED);
		drawable_headers.add(HEADER_UNMAPPED_READS);
		drawable_headers.add(HEADER_UNPAIRED_READ_DUPLICATES);	
		drawable_headers.add(HEADER_PERCENT_DUPLICATION);	
	}
	
	

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
	
	public ReadDuplicationMetricsReportGenerator(File reportDir, List<String> samples){
		this.reportDir = reportDir;
		this.samples = samples;
	}

	private boolean isValidSampleDir(String spl) {
		for (String s : samples) {
			if(spl.contains(s))
				return true;
		}
		return false;
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
			Matcher m = ReadDuplicationReportDirNamePattern.matcher(reportDir.getName());
			m.matches(); //all reportDir do match at this step
			String cat = m.group(2);
			if(cat == null) cat = "";
			
			log.info("Insert metrics : got category :"+cat );// log.info
			fileCategories.add(cat);
		}
		
		PrintWriter htmlpw = null;
		try{
			//writer for html report
			htmlSummaryFile = new File(outDir, "ReadDuplicationMetricsReport.html");
			htmlpw = new PrintWriter(htmlSummaryFile);
			
			htmlpw.println("<!DOCTYPE html><head>");
			htmlpw.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />");
			//---> with customized css
			htmlpw.println("<script type='text/javascript' src='http://www.google.com/jsapi'></script>");
			htmlpw.println(GenerateNGSQCReportAfterGalaxyWF.getHmtlInclude(1) );
			htmlpw.println("<link rel='stylesheet' type='text/css' href='http://cdn.datatables.net/1.10.5/css/jquery.dataTables.min.css'>");
			// <----
			/*
			htmlpw.println("<script type='text/javascript' src='http://www.google.com/jsapi'></script>");
			htmlpw.println("<script type=\"text/javascript\" src=\"../js/jquery.min.js\"></script>");
			htmlpw.println("<script type=\"text/javascript\" src=\"../js/jquery.dataTables.min.js\"></script>");
			htmlpw.println("<script type='text/javascript' src='../js/attc.googleCharts.js'></script> ");
			htmlpw.println("<link rel='stylesheet' href='../css/style.css' type='text/css' > ");
			htmlpw.println("<link rel='stylesheet' href='../css/jquery.dataTables.min.css' type='text/css' > ");
			*/
			// Style for DataTable online
			//htmlpw.println("<link rel='stylesheet' type='text/css' href='http://cdn.datatables.net/1.10.5/css/jquery.dataTables.min.css'>");
			
			// To display text in a bull
			//htmlpw.println("<script type='text/javascript' src='../js/opentip-jquery.min.js'></script>");
			//htmlpw.println("<link rel='stylesheet' type='text/css' href='../css/opentip.css' />");
			
			htmlpw.println("</head><body>");
			
			htmlpw.println("<h1>Read Duplication Metrics Report (Paired-end data)</h1>");
			
			//we drive the extraction by cat and sample name
			for (String cat : fileCategories) {

				htmlpw.println("<h2>Read Duplication collected on "+(cat.length() > 0 ? cat.replaceFirst("_", "") +" bam files" : "default bam files")+"</h2>");
				
				//use a linked map to preserve sample order
				LinkedHashMap<String, CSVLine> lhm = new LinkedHashMap<String, CSVLine>();
				HashMap<String, File> sample2dir = new HashMap<String, File>();
				HashMap<String, File> sample2htmlReportFile = new HashMap<String, File>();
			
				
				for (String spl : samples) {
					File htmlreport = null; // the html report file in the sample dir
					File theone = null; // the sample dir
					//find the dir
					boolean unambigousSampleIdentified = true;
					for (File reportDir : subdirs) {
						Matcher m = ReadDuplicationReportDirNamePattern.matcher(reportDir.getName());
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
							log.warn("Could not find Dup Metrics result directory for sample '"+spl+"' in analysis category '"+cat+"'");
						else
							log.error("More than one Dup Metrics result directory matches for sample '"+spl+"' in analysis category '"+cat+"'");
						sample2dir.put(spl, null);
						sample2htmlReportFile.put(spl, null);
						lhm.put(spl, null);
						continue;
					}
					
					//extract info from report file
					File report = new File(theone, "MarkDuplicates.metrics.txt");
					//htmlreport = theone.listFiles(FileUtil.getFileFilter("Duplication_metrics", "html"))[0];
					//ADD
					File[] tmp = theone.listFiles(FileUtil.getFileFilter("Duplication_metrics", "html"));
					//File htmlreport = null;
					
					if (tmp!=null && tmp.length >0){
						htmlreport = tmp[0];
					}
					
					/*
					 for (String spl : samples) {
					//get the dir
					 File theone = new File(reportDir, cat2spl2dirname.get(cat).get(spl));
					
					//extract info from report file
					File report = new File(theone, "CollectAlignmentSummaryMetrics.metrics.txt");
					File[] tmp=theone.listFiles(FileUtil.getFileFilter("algmnt_metrics", "html"));
					File htmlreport = null;
					if(tmp!=null && tmp.length > 0){
						htmlreport = tmp[0];
					}
					CSVParser p = null;
					 */
					
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
					File summaryResultTable = new File(outDir, "Read_Duplication_metrics_Summary"+cat+".txt");
					pw = new PrintWriter(summaryResultTable);
					Map<String, String> parameters = new LinkedHashMap<String, String>();
					parameters.put("Sample List", Arrays.toString(samples.toArray()));
					FileUtil.addGeneratorMetadataAsComments("#", this, parameters, pw);
					
					//add in html
					htmlpw.println("<h3>Result table</h3>");
					
					htmlpw.println("<div>");
					
					htmlpw.println("<div class=\"mainContent\"><table id='"+HTML_TABLE_ID+"' summary='Description'"
							+" data-attc-createChart=\'true\' data-attc-colDescription=\'Description\'  data-attc-colValues=\'Values1,Values2\' data-attc-location=\'NGSPlot\' data-attc-hideTable=\'false\'"
							+ " data-attc-type=\'column\' data-attc-googleOptions=\'{\"is3D\":true}\' data-attc-controls=\'{\"showHide\":true,\"create\":false,\"chartType\":true}\' class=\"display\">");
					
					int val = 1;
					int n = 0;
					String lnk = null;
					for (Entry<String, CSVLine> e : lhm.entrySet()) {
					
						if(n==0){
							
							htmlpw.println("<thead>");
							htmlpw.println("<tr>");
							
							int tabLength = e.getValue().getRawcols().length; // without 'Sample' and 'Individual report'
							//System.out.println(tabLength); // = 9 ; need 9-1 columns

							String columns = "<th id='Description' class='notDrawable'>"+e.getKey()+"<div class='sortMask'></div></th>";
							
							for(int i=0; i <=tabLength-1; i++){
								
								String header = e.getValue().getValue(i);
								
								String thTag = "<th ";
							
								if(header.equals(HEADER_UNPAIRED_READS_EXAMINED) || header.equals(HEADER_UNMAPPED_READS)){
									thTag += " id='Values"+val+"' ";
									val++;
								}
								if(drawable_headers.contains(header)){
									thTag += " class='drawable' ";
								}else{
									thTag += " class='notdrawable' ";
								}
								thTag += "><div class='sortMask'></div>"+header.replaceAll("_", " ")+"</th>";
								
								columns+= thTag;
								
							}
							
							// to draw the table with class 
							htmlpw.println(columns+"<th class='notDrawable'>Individual Report Link<div class='sortMask'></div></th>");
							htmlpw.println("</tr>");
							htmlpw.println("</thead><tbody>");
							
						}
						
						else{
							
							if (lnk != null){
								lnk = "./" +sample2dir.get(e.getKey()).getName()+ "/"+ sample2htmlReportFile.get(e.getKey()).getName() ;
							}
							
							htmlpw.println("<tr>");
							htmlpw.println(
									"<td>"+e.getKey()+"</td><td>"+e.getValue().merge("</td><td>")+"</td>"+
											"<td><a href=\"" +lnk+ "\" target=\"_blank\">Open Report</a></td>"
									);
							htmlpw.println("</tr>");
						}
						
						n++;
					}
					
					htmlpw.println("</tbody>");
					htmlpw.println("</table>");
					//ADD to googleChart
					htmlpw.println("<div class=\"centerDiv\" style=\"left:2%;\" id=NGSPlot></div>");	
					htmlpw.println("</div></div>");
					htmlpw.println("<script>");
					htmlpw.println(" $('th').on(\"click.DT\", function (e) {if (!$(e.target).hasClass('sortMask')) {e.stopImmediatePropagation();}}); " );
					htmlpw.println("if(typeof google != 'undefined' && google && google.load){	 $(document).ready(function(){$('#"+HTML_TABLE_ID+"').attc();}); } " );
					htmlpw.println("$(document).ready(function() { $('#"+HTML_TABLE_ID+"').DataTable({\"drawCallback\": function(settings){ $('#"+HTML_TABLE_ID+"').attr('data-attc-controls','{\"showHide\":false,\"create\":false,\"chartType\":false}'); if(typeof google != 'undefined' && google && google.load){  $(this).attc(); } },  \"processing\": true, \"paging\": false,\"info\": false}); } );" );
					htmlpw.println("</script>");
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
	public static String sampleNameFromReadDuplicationReportDirName(String name) {
		Matcher m = ReadDuplicationReportDirNamePattern.matcher(name);	
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
