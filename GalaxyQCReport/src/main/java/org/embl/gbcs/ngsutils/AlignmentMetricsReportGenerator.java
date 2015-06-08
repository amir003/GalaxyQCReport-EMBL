package org.embl.gbcs.ngsutils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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

import org.embl.cg.utilitytools.utils.FileUtil;
import org.embl.cg.utilitytools.utils.parser.csv.CSVLine;
import org.embl.cg.utilitytools.utils.parser.csv.CSVParser;
import org.embl.cg.utilitytools.utils.parser.csv.InvalidCSVSetUpException;

public class AlignmentMetricsReportGenerator {

	
	private static final String HTML_TABLE_ID = "alignMetricsTable";

	
	//pattern like "Duplication_metrics_<sample>_bowtie2<extrainfo>" with <extrainfo> being optional
	public static Pattern AlignmentReportDirNamePattern = Pattern.compile("^algmnt_metrics_(.+)_"+GenerateNGSQCReportAfterGalaxyWF.MAPPERNAME+".*$");
	
	
	public static final String HEADER_SAMPLE = "Sample";
	public static final String HEADER_TOTAL_READS = "TOTAL_READS";
	public static final String HEADER_PF_READS = "PF_READS";
	public static final String HEADER_PCT_PF_READS = "PCT_PF_READS";
	public static final String HEADER_PF_NOISE_READS = "PF_NOISE_READS";
	public static final String HEADER_PF_READS_ALIGNED = "PF_READS_ALIGNED";
	public static final String HEADER_PCT_PF_READS_ALIGNED = "PCT_PF_READS_ALIGNED";
	public static final String HEADER_PCT_PF_ALIGNED_BASES = "PCT_PF_ALIGNED_BASES";
	public static final String HEADER_PF_ALIGNED_BASES = "PF_ALIGNED_BASES";
	public static final String HEADER_PF_HQ_ALIGNED_READS = "PF_HQ_ALIGNED_READS";
	public static final String HEADER_PF_HQ_ALIGNED_BASES = "PF_HQ_ALIGNED_BASES";
	public static final String HEADER_PF_HQ_ALIGNED_Q20_BASES = "PF_HQ_ALIGNED_Q20_BASES";
	public static final String HEADER_PF_HQ_MEDIAN_MISMATCHES = "PF_HQ_MEDIAN_MISMATCHES";
	public static final String HEADER_PF_MISMATCH_RATE = "PF_MISMATCH_RATE";
	public static final String HEADER_PF_HQ_ERROR_RATE = "PF_HQ_ERROR_RATE";
	public static final String HEADER_PF_INDEL_RATE = "PF_INDEL_RATE";
	public static final String HEADER_MEAN_READ_LENGTH = "MEAN_READ_LENGTH";
	public static final String HEADER_READS_ALIGNED_IN_PAIRS = "READS_ALIGNED_IN_PAIRS";
	public static final String HEADER_PCT_READS_ALIGNED_IN_PAIRS = "PCT_READS_ALIGNED_IN_PAIRS";
	public static final String HEADER_BAD_CYCLES = "BAD_CYCLES";
	public static final String HEADER_STRAND_BALANCE = "STRAND_BALANCE";
	public static final String HEADER_PCT_CHIMERAS = "PCT_CHIMERAS";
	public static final String HEADER_PCT_ADAPTER = "PCT_ADAPTER";
	public static final String HEADER_SAMPLE_NUM = "SAMPLE";
	public static final String HEADER_LIBRARY = "LIBRARY";
	public static final String HEADER_READ_GROUP = "READ_GROUP";
	
	
	public static Set<String> drawable_headers = new TreeSet<String>();
	static{
		drawable_headers.add(HEADER_TOTAL_READS);
		drawable_headers.add(HEADER_PF_READS);
		drawable_headers.add(HEADER_PF_NOISE_READS);
		drawable_headers.add(HEADER_PF_READS_ALIGNED);
		drawable_headers.add(HEADER_PCT_PF_READS_ALIGNED);
		drawable_headers.add(HEADER_PCT_PF_ALIGNED_BASES);
		drawable_headers.add(HEADER_PF_ALIGNED_BASES);
		drawable_headers.add(HEADER_PF_HQ_ALIGNED_READS);
		drawable_headers.add(HEADER_PF_HQ_ALIGNED_BASES);
		drawable_headers.add(HEADER_PF_HQ_ALIGNED_Q20_BASES);
		drawable_headers.add(HEADER_PF_MISMATCH_RATE);
		drawable_headers.add(HEADER_PF_HQ_ERROR_RATE);
		drawable_headers.add(HEADER_PF_INDEL_RATE);
		drawable_headers.add(HEADER_STRAND_BALANCE);
		drawable_headers.add(HEADER_PCT_ADAPTER);
	
	}
	
	private String[] addBullText = {"Category",
			 "Total Reads",
			 "Passing Filter Reads", // 'Purity Filtred' or 'Passing Illumina's Filter'
			 "Percentage of Passing Filter Reads",
			 "Passing Filter Noise Reads",
			 "Passing Filter Reads Aligned",
			 "Percentage of Passing Filter Reads Aligned",
			 "Passing Filter Aligned Bases",
			 "Passing Filter Aligned Reads (High Quality)",
			 "Passing Filter Aligned Bases (High Quality)",
			 "Passing Filter Aligned Q20 Bases (High Quality)",
			 "Passing Filter Median Mismatches (High Quality)",
			 "Passing Filter Mismatch Rate",
			 "Passing Filter Error Rate (High Quality)",
			 "Passing Filter Indel Rate",
			 "Mean Read Length",
			 "Reads Aligned in Pairs",
			 "Percentage of Reads Aligned in Pairs",
			 "Bad Cycles",
			 "Strand Balance",
			 "Percentage of Chimeras",
			 "Percentage of Adapter",
			 "Sample",
			 "Library",
			 "Read Group",
			};

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
	
	

	public AlignmentMetricsReportGenerator(File reportDir, List<String> samples){
		this.reportDir = reportDir;
		this.samples = samples;
	}

	public void generateReport(File outDir) {
		
		//get list of dirs
		List<File> subdirs = new ArrayList<File>();
		Map<String, Map<String, String>> cat2spl2dirname = new HashMap<String, Map<String, String>>();
		//to avoid multi matching ie sample names being a substring of another one, we iterate over sample names from the longuest to the shortest
		Collections.sort(
				samples, 
				new Comparator<String>() {
					public int compare(String s1, String s2) {
						Integer i1 = s1.length();
						Integer i2 = s2.length();
						return i2.compareTo(i1);
					}
				}
		);
		for(File f : reportDir.listFiles()){
			if(f.isDirectory()){
				String ext_spl = sampleNameFromReadDuplicationReportDirName(f.getName()); //thsi returns more than sample names
				//System.out.println(f.getName() + "=> ext_spl is "+ext_spl);
				//System.out.println(f.getName() + "=> spl is "+spl);
				String theSpl = null;
				for (String spl : samples) {
					//System.out.println("eval spl '"+spl+"'");
					if(ext_spl.contains(spl)){
						//System.out.println(" spl is "+spl);
						theSpl = spl;
						subdirs.add(f);
						break;
					}
				}
				//get category
				String cat = ext_spl.replace(theSpl, "").replaceAll("_", " ").trim().replaceAll(" ", "_"); //eliminate trailing "_"
				if(!cat2spl2dirname.containsKey(cat))
					cat2spl2dirname.put(cat, new HashMap<String, String>());
				cat2spl2dirname.get(cat).put(theSpl, f.getName());
				fileCategories.add(cat);
			}
		}
		
		//resort samples the normal way
		Collections.sort(samples);
		
		PrintWriter htmlpw = null;
		try{
			//writer for html report
			htmlSummaryFile = new File(outDir, "ReadAlignmentMetricsReport.html");
			htmlpw = new PrintWriter(htmlSummaryFile);
			htmlpw.println("<!DOCTYPE html><head>");
			htmlpw.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />");
			htmlpw.println("<script type='text/javascript' src='http://www.google.com/jsapi'></script>");			
			//htmlpw.println("<script src='http://ajax.googleapis.com/ajax/libs/jquery/1.11.2/jquery.min.js'></script>");
			//htmlpw.println("<script type='text/javascript' src='http://cdn.datatables.net/1.10.5/js/jquery.dataTables.min.js'></script>");
			htmlpw.println(GenerateNGSQCReportAfterGalaxyWF.getHmtlInclude(1)); // To add the libraries locally stored
			// Style for DataTable online
			htmlpw.println("<link rel='stylesheet' type='text/css' href='http://cdn.datatables.net/1.10.5/css/jquery.dataTables.min.css'>");
			
			// To display text in a bull
			htmlpw.println("<script type='text/javascript' src='../js/opentip-jquery.min.js'></script>");
			htmlpw.println("<link rel='stylesheet' type='text/css' href='../css/opentip.css' />");
			
			htmlpw.println("</head><body>");
			
				   
			htmlpw.println("<h1>Read Alignment Metrics Report </h1>");
			
			//we drive the extraction by cat and sample name
			for (String cat : fileCategories) {

				htmlpw.println("<h2>Read Duplication collected on '"+(cat.length() > 0 ? cat.replaceFirst("_", "") +"' files" : "default files")+"</h2>");
				
				//use a linked map to preserve sample order
				LinkedHashMap<String, List<CSVLine>> lhm = new LinkedHashMap<String, List<CSVLine>>();
				HashMap<String, File> sample2dir = new HashMap<String, File>();
				HashMap<String, File> sample2htmlReportFile = new HashMap<String, File>();
				
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
					try {
						p = new CSVParser(-1, -1, true);
						p.setStoreCommentLines(true);
						
						Iterator<CSVLine> lines = p.iterator(report);
						List<CSVLine> csvlines = new ArrayList<CSVLine>();
						int n =0 ;
						while (lines.hasNext()) {
							CSVLine l = lines.next();
							if(l.isHeaderLine() ){
								if(n==0 && !lhm.containsKey(GenerateNGSQCReportAfterGalaxyWF.SAMPLE_HEADER)){
									List<CSVLine> _l = new ArrayList<CSVLine>();
									_l.add(l);
									lhm.put(GenerateNGSQCReportAfterGalaxyWF.SAMPLE_HEADER, _l);
								}
							}
							else{
								csvlines.add(l);
							}
							n++;
						}
						sample2dir.put(spl, theone);
						sample2htmlReportFile.put(spl, htmlreport);
						lhm.put(spl, csvlines);
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
					File summaryResultTable = new File(outDir, "Alignment_metrics_Summary_"+cat+".txt");
					pw = new PrintWriter(summaryResultTable);
					Map<String, String> parameters = new LinkedHashMap<String, String>();
					parameters.put("Sample List", Arrays.toString(samples.toArray()));
					FileUtil.addGeneratorMetadataAsComments("#", this, parameters, pw);
					
					//add in html
					htmlpw.println("<h3>Result table</h3>");
					
					htmlpw.println("<div>");
					htmlpw.println("<div class=\"mainContent\"><table id='"+HTML_TABLE_ID+"' summary='Description'"
							+" data-attc-createChart=\'true\' data-attc-colDescription=\'Description\'  data-attc-colValues=\'Values1,Values2,Values3\' data-attc-location=\'NGSPlot\' data-attc-hideTable=\'false\'"
							+ " data-attc-type=\'column\' data-attc-googleOptions=\'{\"is3D\":true}\' data-attc-controls=\'{\"showHide\":true,\"create\":false,\"chartType\":true}\' class=\"display\">");
					
					
					int val=1;
					int n = 0;
					String lnk = null;
					for (Entry<String, List<CSVLine>> e : lhm.entrySet()) {
						for(CSVLine _l : e.getValue()){
							pw.println(e.getKey()+"\t"+_l.merge("\t"));
						}
						
						if(n==0){
							htmlpw.println("<thead>");
							htmlpw.println("<tr>");
							for(CSVLine _l : e.getValue()){
								String columns = "<th id='Description' class='notdrawable'><div data-ot='You can sort your data' data-ot-background ='#A9F5F2' data-ot-delay='1.0'  >"+e.getKey()+"</div><div class='sortMask'></div></th>";
								int i = 0;
								for( String header : _l.getRawcols()){
									String thTag = "<th ";
									
									if(header.equals(HEADER_TOTAL_READS) || header.equals(HEADER_PF_READS_ALIGNED)|| header.equals(HEADER_PF_HQ_ALIGNED_READS) ){
										thTag += " id='Values"+val+"' ";
										val ++;
									}
									if(drawable_headers.contains(header)){
										thTag += " class='drawable' ";
									}else{
										thTag += " class='notdrawable' ";
									}
									thTag += "><div class='sortMask'></div><div data-ot='"+addBullText[i]+"' data-ot-background ='#A9F5F2' data-ot-delay='1.0'  >"+header.replaceAll("_", " ")+"</div></th>";
									// <div data-ot='"+addBullText[i]+"' data-ot-background ='#A9F5F2' data-ot-delay='1.0'  > ... </div>
									columns+= thTag;
									i++;
								}
							
								htmlpw.println(columns+"<th class='notdrawable' >Individual Report Link<div class=\"sortMask\"></div></th>");								
								
							}
							htmlpw.println("</tr>");
							htmlpw.println("</thead><tbody>");
							
							
						}
						else{
							
							if(lnk != null){
								lnk ="./" +sample2dir.get(e.getKey()).getName()+ "/"+ sample2htmlReportFile.get(e.getKey()).getName();
							}
							
							for(CSVLine _l : e.getValue()){
								htmlpw.println("<tr>");
								htmlpw.println(
										"<td>"+e.getKey()+"</td><td>"+_l.merge("</td><td>")+"</td>"+
												"<td><a href=\"" +lnk+ "\" target=\"_blank\">Open Report</a></td>"
										);
								htmlpw.println("</tr>");
							}
						}
						
						n++;
					}
					htmlpw.println("</tbody>");
					htmlpw.println("</table>");
					//googleChart
					htmlpw.println("<div class=\"centerDiv\" style=\"left:30%;\" id=NGSPlot></div>");	
					htmlpw.println("</div></div>");
					htmlpw.println("<script>");
					htmlpw.println(" $('th').on(\"click.DT\", function (e) {if (!$(e.target).hasClass('sortMask')) {e.stopImmediatePropagation();}}); " );
					htmlpw.println("$(document).ready(function() { $('#"+HTML_TABLE_ID+"').DataTable({  \"drawCallback\": function(settings){ if(typeof google != 'undefined' && google && google.load){  $(this).attc();} $('#"+HTML_TABLE_ID+"').attr('data-attc-controls','{\"showHide\":false,\"create\":false,\"chartType\":false}'); }, \"processing\": true, \"paging\": false,\"info\": false}); } );" );
					htmlpw.println("if(typeof google != 'undefined' && google && google.load){	 $(document).ready(function(){$('#"+HTML_TABLE_ID+"').attc();}); } " );
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
	 * @return the extended sample name if dir name matches the expected dir name pattern or null
	 */
	public static String sampleNameFromReadDuplicationReportDirName(String name) {
		Matcher m = AlignmentReportDirNamePattern.matcher(name);	
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
