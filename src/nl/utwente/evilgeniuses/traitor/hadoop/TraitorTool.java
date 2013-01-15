package nl.utwente.evilgeniuses.traitor.hadoop;

import java.net.URI;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;
import org.commoncrawl.hadoop.mapred.ArcInputFormat;


/**
 * Uses the CommonCrawl web archive data to run the "Traitor" analysis tool.
 * 
 * @author participant
 * 
 * @see nl.utwente.evilgeniuses.traitor
 */
public class TraitorTool extends Configured implements Tool {

	protected static final Logger LOG = Logger.getLogger(TraitorTool.class);
	private static final String ARGNAME_INPATH = "-in";
	private static final String ARGNAME_OUTPATH = "-out";
	private static final String ARGNAME_CONF = "-conf";
	private static final String ARGNAME_OVERWRITE = "-overwrite";
	private static final String ARGNAME_MAXFILES = "-maxfiles";
	private static final String ARGNAME_NUMREDUCE = "-numreducers";
	private static final String ARGNAME_COMPRESS = "-compress";
	private static final String FILEFILTER = ".arc.gz";

	protected static enum MAPPERCOUNTER {
		RECORDS_IN, EMPTY_PAGE_TEXT, EXCEPTIONS
	}

	protected void usage() {
		System.out.println("\n  " + getClass().getCanonicalName() + " \n"
				+ "                           " + ARGNAME_INPATH
				+ " <inputpath>\n" + "                           "
				+ ARGNAME_OUTPATH + " <outputpath>\n"
				+ "                         [ " + ARGNAME_OVERWRITE + " ]\n"
				+ "                         [ " + ARGNAME_NUMREDUCE
				+ " <number_of_reducers> ]\n" + "                         [ "
				+ ARGNAME_CONF + " <conffile> ]\n"
				+ "                         [ " + ARGNAME_MAXFILES
				+ " <maxfiles> ]\n" + "                         [ "
				+ ARGNAME_COMPRESS + " ]");
		System.out.println("");
		GenericOptionsParser.printGenericCommandUsage(System.out);
	}

	/**
	 * Implmentation of Tool.run() method, which builds and runs the Hadoop job.
	 * 
	 * @param args
	 *            command line parameters, less common Hadoop job parameters
	 *            stripped out and interpreted by the Tool class.
	 * @return 0 if the Hadoop job completes successfully, 1 if not.
	 */
	@Override
	public int run(String[] args) throws Exception {
		String inputPath = null;
		String outputPath = null;
		String configFile = null;
		boolean overwrite = false;
		boolean compressed = false;
		int numReducers = 60;

		// Read the command line arguments. We're not using GenericOptionsParser
		// to prevent having to include commons.cli as a dependency.
		for (int i = 0; i < args.length; i++) {
			try {
				if (args[i].equals(ARGNAME_INPATH)) {
					inputPath = args[++i];
				} else if (args[i].equals(ARGNAME_OUTPATH)) {
					outputPath = args[++i];
				} else if (args[i].equals(ARGNAME_CONF)) {
					configFile = args[++i];
				} else if (args[i].equals(ARGNAME_MAXFILES)) {
					SampleFilter.setMax(Long.parseLong(args[++i]));
				} else if (args[i].equals(ARGNAME_OVERWRITE)) {
					overwrite = true;
				} else if (args[i].equals(ARGNAME_NUMREDUCE)) {
					numReducers = Integer.parseInt(args[++i]);
				} else if (args[i].equals(ARGNAME_COMPRESS)) {
					compressed = true;
				} else {
					LOG.warn("Unsupported argument: " + args[i]);
				}
			} catch (ArrayIndexOutOfBoundsException e) {
				usage();
				throw new IllegalArgumentException();
			}
		}

		if (inputPath == null || outputPath == null) {
			usage();
			throw new IllegalArgumentException();
		}

		// Read in any additional config parameters.
		if (configFile != null) {
			LOG.info("adding config parameters from '" + configFile + "'");
			this.getConf().addResource(configFile);
		}

		// Create the Hadoop job.
		Configuration conf = getConf();
		conf.setLong("mapred.task.timeout", 3600*60*60);
		Job job = new Job(conf);
		job.setJarByClass(TraitorTool.class);
		job.setNumReduceTasks(numReducers);

		// Scan the provided input path for ARC files.
		LOG.info("setting input path to '" + inputPath + "'");
		SampleFilter.setFilter(FILEFILTER);
		FileInputFormat.addInputPath(job, new Path(inputPath));
		FileInputFormat.setInputPathFilter(job, SampleFilter.class);

		// Delete the output path directory if it already exists and user wants
		// to overwrite it.
		if (overwrite) {
			LOG.info("clearing the output path at '" + outputPath + "'");
			FileSystem fs = FileSystem.get(new URI(outputPath), conf);
			if (fs.exists(new Path(outputPath))) {
				fs.delete(new Path(outputPath), true);
			}
		}

		// Set the path where final output 'part' files will be saved.
		LOG.info("setting output path to '" + outputPath + "'");
		FileOutputFormat.setOutputPath(job, new Path(outputPath));
		FileOutputFormat.setCompressOutput(job, compressed);

		// Set which InputFormat class to use.
		job.setInputFormatClass(ArcInputFormat.class); // SequenceFileInputFormat.class

		// Set which OutputFormat class to use.
		job.setOutputFormatClass(TextOutputFormat.class);

		// Set the output data types.
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(LongWritable.class);

		// Set which Mapper and Reducer classes to use.
		job.setMapperClass(TraitorMapper.class);
		job.setCombinerClass(LBoundedLongSumReducer.class);
		job.setReducerClass(LBoundedLongSumReducer.class);

		// Set the name of the job.
		job.setJobName("Norvig Award - (13) - Evil Geniuses' Traitor");

		boolean success = job.waitForCompletion(true);
		return success ? 0 : 1;
	}

	/**
	 * Main entry point that uses the {@link ToolRunner} class to run the
	 * example Hadoop job.
	 */
	public static void main(String[] args) throws Exception {
		int res = ToolRunner.run(new Configuration(), new TraitorTool(), args);
		System.exit(res);
	}

}
