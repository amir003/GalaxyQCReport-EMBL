package org.embl.gbcs.ngsutils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.embl.cg.utilitytools.utils.FileUtil;
import org.embl.cg.utilitytools.utils.parser.csv.CSVLine;
import org.embl.cg.utilitytools.utils.parser.csv.CSVParser;
import org.embl.cg.utilitytools.utils.parser.csv.InvalidCSVSetUpException;

public class SPPStrandCrossCorrelationMetricsReportGenerator {
	private static Logger log = Logger.getLogger(SPPStrandCrossCorrelationMetricsReportGenerator.class);
	
	
	private static final String HTML_TABLE_ID = "StrandCrossCorrMetricsTable";
	//private static String HTML_TABLE_ID = "StrandCrossCorrMetricsTable";
		
	public static final String HEADER_SAMPLE = "Sample";
	public static final String HEADER_ReadNum = "ReadNum";
	public static final String HEADER_Cross_Cor_Summits = "Cross-Cor_Summits";
	public static final String HEADER_Cross_Cor_Values = "Cross-Cor_Values";
	public static final String HEADER_Phantom_Loc = "Phantom_Loc";
	public static final String HEADER_Phantom_Cross_Cor = "Phantom_Cross-Cor";
	public static final String HEADER_Max_Shift = "Max Shift";
	public static final String HEADER_Min_Cross_Cor = "Min_Cross-Cor";
	public static final String HEADER_NSC = "NSC";
	public static final String HEADER_RSC = "RSC";
	public static final String HEADER_qTag = "qTag";
	
	public static Set<String> drawable_headers = new TreeSet<String>();
	static{
		drawable_headers.add(HEADER_ReadNum);
		drawable_headers.add(HEADER_Phantom_Cross_Cor);
		drawable_headers.add(HEADER_Min_Cross_Cor);	
		drawable_headers.add(HEADER_NSC);	
		drawable_headers.add(HEADER_RSC);	
		
		//drawable_headers.add(HEADER_qTag);	
	}
	
	
	private static final String[] CROSS_PEAKSHIFT_FILE_HEADERS = new String[]{
			"Dataset", "ReadNum", "Cross-Cor_Summits", "Cross-Cor_Values", "Phantom_Loc","Phantom_Cross-Cor",
			"Max Shift", "Min_Cross-Cor","NSC","RSC","qTag"};


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
		
		public SPPStrandCrossCorrelationMetricsReportGenerator(File reportDir, List<String> samples){
			this.reportDir = reportDir;
			this.samples = samples;
		}

		public void generateReport(File outDir) {
			
			//prepare common report file 
			PrintWriter htmlpw = null;
			
			try{
				//writer for html report
				htmlSummaryFile = new File(outDir, "SPP_Strand_Cross-Correlation_MetricsReport.html");
				htmlpw = new PrintWriter(htmlSummaryFile);
				
				htmlpw.println("<!DOCTYPE html><head>");
				htmlpw.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />");
				// New libraries used
				htmlpw.println("<script type='text/javascript' src='http://www.google.com/jsapi'></script>");
				//htmlpw.println("<script src='http://ajax.googleapis.com/ajax/libs/jquery/1.11.2/jquery.min.js'></script>");
				htmlpw.println("<script type=\"text/javascript\" src=\"../../js/jquery.min.js\"></script>"); //Local
				//htmlpw.println("<script type='text/javascript' src='http://cdn.datatables.net/1.10.5/js/jquery.dataTables.min.js'></script>");
				htmlpw.println("<script type=\"text/javascript\" src=\"../../js/jquery.dataTables.min.js\"></script>"); // Local
				//htmlpw.println(GenerateNGSQCReportAfterGalaxyWF.getHmtlInclude(2)); // To unload the same css file than the other
				htmlpw.println("<script type='text/javascript' src='../../js/attc.googleCharts.js'></script> ");
				htmlpw.println("<link rel='stylesheet' href='../../css/style.css' type='text/css' > ");
				// Style for DataTable : comment the line below to don't use this style
				htmlpw.println("<link rel='stylesheet' href='../../css/jquery.dataTables.min.css' type='text/css' > ");
				htmlpw.println("<link rel='stylesheet' type='text/css' href='http://cdn.datatables.net/1.10.5/css/jquery.dataTables.min.css'>");
				// To display text in a bull
				//htmlpw.println("<script type='text/javascript' src='../js/opentip-jquery.min.js'></script>");
				//htmlpw.println("<link rel='stylesheet' type='text/css' href='../css/opentip.css' />");
				htmlpw.println("</head><body>");
				
				htmlpw.println("<h1>SPP Strand Cross-Correlation Metrics Report (Paired-end data)</h1>");
				
				//categories are organized in separate subdir
				int catNumber = 1; // to increment the table id number
				for(File f : reportDir.listFiles()){
					if(!f.isDirectory())
						continue;
					String cat = f.getName();
					fileCategories.add(cat);
					HashMap<String, File> sample2pdf = new HashMap<String, File>();
					HashMap<String, File> sample2txtreport = new HashMap<String, File>();
					//list files in dir and fill in maps
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
					//pdf
					for(File pdfFile : f.listFiles(FileUtil.getFileFilter("SPP_cross-cor", "pdf"))){
						//find the sample
						for (String spl : samples) {
							if(pdfFile.getName().contains(spl)){
								sample2pdf.put(spl,  pdfFile);
								break;
							}
						}
					}
					//txt
					for(File txtFile : f.listFiles(FileUtil.getFileFilter("SPP_cross-cor", "txt"))){
						//find the sample
						for (String spl : samples) {
							if(txtFile.getName().contains(spl)){
								sample2txtreport.put(spl,txtFile);
								break;
							}
						}
					}
					
					//resort samples the way it should
					Collections.sort(samples);
					
					generateForCategory(htmlpw, cat, catNumber, sample2txtreport, sample2pdf, outDir);
					//generateForCategory(htmlpw, cat, sample2txtreport, sample2pdf, outDir);
					catNumber += 1;
				}

				//finalize report file
				htmlpw.println("</body></html>");

			}catch (IOException e) {
				throw new RuntimeException(e);
			}finally{
				if(htmlpw!=null)
					htmlpw.close();
			}
				
		}

		private void generateForCategory(
				PrintWriter htmlpw, 
				String cat,
				int catNumber,
				HashMap<String, File> sample2txtreport,
				HashMap<String, File> sample2pdf, 
				File outDir
				) {
			
			//System.out.println("INTO ----> :"+catNumber);
			log.info("Generate for SPP CC for category : "+cat); // log.info
			htmlpw.println("<h2>Strand Cross-Correlation collected on "+(cat.length() > 0 ? cat.replaceFirst("_", "") +" bam files" : "default bam files")+"</h2>");
			
			//use a linked map to preserve sample order
			LinkedHashMap<String, CSVLine> lhm = new LinkedHashMap<String, CSVLine>();
			//add the headers (the first value column will be clipped off)
			CSVLine h = new CSVLine("fake", 1, CROSS_PEAKSHIFT_FILE_HEADERS, "\t");
			lhm.put(
					GenerateNGSQCReportAfterGalaxyWF.SAMPLE_HEADER, 
					h);
			for (String spl : samples) {
				//extract info from report file
				CSVParser p = null;
				CSVLine line = null; // the unique result line from file
				try {
					p = new CSVParser(-1, -1, false);
					//these files have a single line
					if(sample2txtreport.containsKey(spl) && sample2txtreport.get(spl)!=null){
						lhm.put(spl, p.parse(sample2txtreport.get(spl).getAbsolutePath(), "\t")[0]);
					}else{
						lhm.put(spl, null);
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
				File summaryResultTable = new File(outDir, "summary_spp_crosscorrel_"+cat+".txt");
				pw = new PrintWriter(summaryResultTable);
				Map<String, String> parameters = new LinkedHashMap<String, String>();
				parameters.put("Sample List", Arrays.toString(samples.toArray()));
				FileUtil.addGeneratorMetadataAsComments("#", this, parameters, pw);
				
				//add in html
				htmlpw.println("<h3>Result table</h3>");
				htmlpw.println("<div>");
				
				//htmlpw.println("<div class='mainContent'>");
				//htmlpw.println("<table id='"+HTML_TABLE_ID+"' class=\"tablesorter\">");
				
				String location = "AttendancePercentagesPie"+catNumber;
				String idTable = HTML_TABLE_ID+"_"+catNumber;
				
				// if a new class would be used to improve the management of 2 big tables, just decomment these lignes and the lines 329 and 330
				//if(catNumber == 2)
					//htmlpw.println("<div class='tablewrapper'>");
				
				htmlpw.println("<div class=\"mainContent\"><table id='"+idTable+"' summary='Description'"
						+" data-attc-createChart=\'true\' data-attc-colDescription=\'Description\'  data-attc-colValues=\'Values\' data-attc-location=\'"+ location+"\' data-attc-hideTable=\'false\'"
						+ " data-attc-type=\'column\' data-attc-googleOptions=\'{\"is3D\":true}\' data-attc-controls=\'{\"showHide\":true,\"create\":false,\"chartType\":true}\' class=\"display\">");
				
				
				int n = 0;
				for (Entry<String, CSVLine> e : lhm.entrySet()) {
					//text report
					if(e.getValue()!=null)
						pw.println(e.getKey()+"\t"+e.getValue().merge("\t", 1, e.getValue().getColNum()));
					else
						pw.println(e.getKey()+"\t"+"No report found");
					
					//html report
					if(n==0){
						htmlpw.println("<thead>");
						htmlpw.println("<tr>");
						
						int tabLength = e.getValue().getRawcols().length; // = 11 ; need 11-1 columns
						
						
						String columns = "<th id='Description' class='notDrawable'>"+e.getKey()+"<div class='sortMask'></div></th>";
						
						for(int i=0; i <=tabLength-1; i++){
							
							String header = e.getValue().getValue(i);
							
							String thTag = "<th ";
						
							if(header.equals(HEADER_ReadNum)){
								thTag += " id='Values' ";
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
						htmlpw.println(columns+"<th class='notDrawable'>Individual Report Link</th>");
						htmlpw.println("</tr>");
						htmlpw.println("</thead><tbody>");
					}
					else{
						htmlpw.println("<tr>");
						CSVLine _l = e.getValue();
						
						if(_l!=null){
							String lnk = "./" +cat+ "/"+sample2pdf.get(e.getKey()).getName() ;
							htmlpw.println(
									"<td>"+e.getKey()+"</td><td>"+e.getValue().merge("</td><td>")+"</td>"+
											"<td><a href=\"" +lnk+ "\" target=\"_blank\">Open PDF</a></td>" 
							);
						}
						else{
							String lnk = "No PDF found";
							htmlpw.println(
									"<td>"+e.getKey()+"</td><td colspan='11'>No report found</td>"+
											"<td>" +lnk+ "</td>" 
							);
						}
						
						htmlpw.println("</tr>");
					}
					
					n++;
				}
				htmlpw.println("</tbody>");
				htmlpw.println("</table>");
				
				
				//ADD to googleChart
				htmlpw.println("<div class=\"centerDiv\"  id="+location+"></div></div>");	//style=\"width:1000px; height:250px;\"
				
				htmlpw.println("</div>");
				
				//if(catNumber == 2)
					//htmlpw.println("</div>");
				// ADD the script to sort using DataTable
				htmlpw.println("<script> if(typeof google != 'undefined' && google && google.load){	$(document).ready(function(){$('#"+idTable+"').attc();}); }" );
				htmlpw.println(" $('th').on(\"click.DT\", function (e) {if (!$(e.target).hasClass('sortMask')) {e.stopImmediatePropagation();}}); " );

				htmlpw.println(" $(document).ready(function() { $('#"+idTable+"').DataTable({\"drawCallback\": function(settings){ $('#"+idTable+"').attr('data-attc-controls','{\"showHide\":false,\"create\":false,\"chartType\":false}'); if(typeof google != 'undefined' && google && google.load){  $(this).attc();} },  \"aoColumnDefs\":[{\"processing\": true, \"bSortable\": false, \"aTargets\": [ 1,3,4 ] }], \"retrieve\": true, \"paging\": false,\"info\": false}); });</script>" ); // Error : \"paging\": false,\"info\": false});
				// To solve : \"retrieve\": true, 
				htmlpw.println("<br/><br/><br/><br/><br/><br/>");
				htmlpw.println("<br/><br/><br/><br/><br/><br/>");
				htmlpw.println("<br/><br/><br/><br/><br/><br/>");
				htmlpw.println("<br/><br/><br/><br/><br/><br/>");
				htmlpw.println("<br/><br/><br/><br/><br/><br/>");
				htmlpw.println("<br/><br/><br/><br/><br/><br/>");
				//idTable += "_"+catNumber;
				//System.out.println(catNumber);
			
				
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally{
				if(pw!=null)
					pw.close();
			}
			
			//htmlpw.println("<script> table.destroy(); </script>" );
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
