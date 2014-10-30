package inra.ijpb.appli.fasga;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Plot;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.strel.OctagonStrel;

import java.awt.Color;
import java.io.File;

/**
 * @author David Legland
 *
 */
public class QuantifFasgaPlugin implements PlugIn {

	/**
	 * Apply following operations on current image:
	 * <ul>
	 * <li>segment stem</li>
	 * <li>normalize background</li>
	 * <li>morphological closing</li>
	 * <li>distance map</li>
	 * <li>divide into regions</li>
	 * <li>compute average red, green and blue in each region</li>
	 * </ul>
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) {
		
		// Check an image was loaded
		ImagePlus image = WindowManager.getCurrentImage();
		if (image == null) {
			IJ.error("No image", "Need at least one image to work");
			return;
		}

		// get base name of image for displaying results
		String name = image.getShortTitle();

		// compute the color profile
		ResultsTable colorProfile = exec(image);
		
		// display data table and color profiles
		colorProfile.show(name + " Colors");
		plotColorProfile(colorProfile, " Colors");
		
		// assess lignification profile from color profile
		ResultsTable lignificationProfile = computeLignificationProfile(colorProfile);

		// Display lignification profile and data table
		lignificationProfile.show(name + " Lignification");
		plotLignificationProfile(lignificationProfile, name);
	}

	public void plotColorProfile(ResultsTable table, String name) {
		// create x values
		int n = table.getCounter();
		double x[] = new double[n];
		double y[] = new double[n];
		for (int i = 0; i < n; i++) {
			x[i] = i + 1;
			y[i] = 0;
		}
		
		// Compute the max extent of all 3 colors
		double yMax = 0;
		for (int i = 0; i < n; i++) {
			yMax = Math.max(yMax, table.getValueAsDouble(0, i));
			yMax = Math.max(yMax, table.getValueAsDouble(1, i));
			yMax = Math.max(yMax, table.getValueAsDouble(2, i));
		}

		// create plot with default line
		String title = name + " Color Profiles";
		Plot plot = new Plot(title, "Region index", "Values", x, y);

		// set up plot
		plot.setLimits(0, n, 0, yMax);

		// Draw each profile
		plot.setColor(Color.RED);
		plot.addPoints(x, table.getColumnAsDoubles(0), Plot.LINE);
		plot.setColor(Color.GREEN);
		plot.addPoints(x, table.getColumnAsDoubles(1), Plot.LINE);
		plot.setColor(Color.BLUE);
		plot.addPoints(x, table.getColumnAsDoubles(2), Plot.LINE);

		// Display in new window
		plot.show();
	}
		
	public void plotLignificationProfile(ResultsTable table, String name) {
		// create x values
		int n = table.getCounter();
		double x[] = new double[n];
		double y[] = new double[n];
		for (int i = 0; i < n; i++) {
			x[i] = i + 1;
			y[i] = 0;
		}
		
		// Compute the max extent of first column
		double yMax = 0;
		for (int i = 0; i < n; i++) {
			yMax = Math.max(yMax, table.getValueAsDouble(0, i));
		}

		// create plot with default line
		String title = name + " Lignification Profiles";
		Plot plot = new Plot(title, "Region index", "Values", x, y);

		// set up plot
		plot.setLimits(0, n, 0, yMax);

		// Draw each profile
		plot.setColor(Color.BLACK);
		plot.addPoints(x, table.getColumnAsDoubles(0), Plot.LINE);

		// Display in new window
		plot.show();
	}
		
		
	public ResultsTable exec(ImagePlus image) {
		ImageProcessor proc = image.getProcessor();
		
		// Segmentation of stem and of background
		IJ.showStatus("Segment Stem...");
		ImageProcessor stem = SegmentStemPlugin.segmentStem(proc);
		
		// Invert stem to get background mask
		ImageProcessor bgMask = stem.duplicate();
		bgMask.invert();

		// Remove vignetting effect on RGB image
//		IJ.showStatus("Normalize Background...");
//		ImageProcessor fitted = BrightBackgroundNormalizer.normalizeBackground(
//				proc, bgMask, 2);
//		new ImagePlus("Normalized", fitted).show();
		ImageProcessor fitted = proc;
		
		// compute morphological opening on each channel
		IJ.showStatus("Morphological Filtering...");
		Strel strel = new OctagonStrel(25);
		strel.showProgress(false);
		ColorProcessor rgbFilt = (ColorProcessor) Morphology.opening(fitted, strel);
		
		String name = image.getShortTitle() + "-filt";
		new ImagePlus(name, rgbFilt).show();

		// Compute distance map to background
		IJ.showStatus("Compute Distance Map...");
		short[] weights = new short[]{3, 4};
		ImageProcessor dist = BinaryImages.distanceMap(stem, weights, true);
		
		IJ.showStatus("Compute classes...");
		ImageProcessor regions = DistanceProfile.distanceMapToClasses(dist, 80);

//		new ImagePlus("Distance Map", dist).show();

		IJ.showStatus("Compute profile");
		ResultsTable table = DistanceProfile.colorByRegion(rgbFilt, regions);
		
		IJ.showStatus("");
		return table;
	}

	/**
	 * Computes the lignification profile from the ratio of red over blue
	 * color profiles
	 */
	public ResultsTable computeLignificationProfile(ResultsTable colors) {
		
		double[] red 	= colors.getColumnAsDoubles(0);
		double[] blue 	= colors.getColumnAsDoubles(2);
		
		// Initialize a new result table
		ResultsTable result = new ResultsTable();
		
		// compute mean values for each region
		double value;
		for (int i = 0; i < red.length; i++) {
			// add an entry to the resulting data table
			result.incrementCounter();
			
			// add mean red
			value = red[i] / blue[i];			
			result.addValue("Lignification", value);
		}
		
		return result;
	}
	
	public static final void main(String[] args) 
	{
		System.out.println("run main");
		
		new ImageJ();
		
		System.out.println(new File(".").getAbsolutePath());
		File file = new File("./src/main/resources/files/maize-crop.tif");
		
		ImagePlus imagePlus = IJ.openImage(file.getPath());
		if (imagePlus == null) 
		{
			throw new RuntimeException("Could not read input image");
		}
		imagePlus.show();
		
		PlugIn plugin = new QuantifFasgaPlugin();
		plugin.run("");
	}
}
