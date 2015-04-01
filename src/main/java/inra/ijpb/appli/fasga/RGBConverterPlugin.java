/**
 * 
 */
package inra.ijpb.appli.fasga;

import java.awt.Color;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/**
 * Plugin for converting a color image in RGB format to another derived channel:
 * hue, saturation, or brightness.
 * 
 * @author dlegland
 *
 */
public class RGBConverterPlugin implements PlugIn 
{

	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) 
	{
		ImagePlus imagePlus = IJ.getImage();
		if (imagePlus == null)
		{
			IJ.error("Requires a RGB Color Image");
			return;
		}
		if (!(imagePlus.getProcessor() instanceof ColorProcessor))
		{
			IJ.error("Requires a RGB Color Image");
			return;
		}
		ColorProcessor colorImage = (ColorProcessor) imagePlus.getProcessor();
		
		ImageProcessor result;
		if (arg.compareToIgnoreCase("hue") == 0) 
		{
			result = computeHue(colorImage);
		}
		else if (arg.compareToIgnoreCase("saturation") == 0) 
		{
			result = computeSaturation(colorImage);
		}
		else if (arg.compareToIgnoreCase("brightness") == 0) 
		{
			result = computeBrightness(colorImage);
		}
		else
		{
			throw new IllegalArgumentException("Could not understand parameter: " + arg);
		}
		
		String newTitle = imagePlus.getShortTitle() + "-" + arg;
		ImagePlus resPlus = new ImagePlus(newTitle, result);
		
		resPlus.show(newTitle);
	}

	private static final FloatProcessor computeHue(ColorProcessor image)
	{
		// get image size
		int width = image.getWidth();
		int height = image.getHeight();
		
		// allocate memory for result
		FloatProcessor result = new FloatProcessor(width, height);
		
		// iterate over pixels
		float[] hsb = new float[3]; 
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int c = image.get(x, y);
				int r = (c & 0xFF0000) >> 16;
				int g = (c & 0xFF00) >> 8;
				int b =  c & 0xFF;
				Color.RGBtoHSB(r, g, b, hsb);
				result.setf(x, y, hsb[0]);
			}
		}
		
		return result;
	}
	
	private static final FloatProcessor computeSaturation(ColorProcessor image)
	{
		// get image size
		int width = image.getWidth();
		int height = image.getHeight();
		
		// allocate memory for result
		FloatProcessor result = new FloatProcessor(width, height);
		
		// iterate over pixels
		float[] hsb = new float[3]; 
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int c = image.get(x, y);
				int r = (c & 0xFF0000) >> 16;
				int g = (c & 0xFF00) >> 8;
				int b =  c & 0xFF;
				Color.RGBtoHSB(r, g, b, hsb);
				result.setf(x, y, hsb[1]);
			}
		}
		
		return result;
	}

	private static final FloatProcessor computeBrightness(ColorProcessor image)
	{
		// get image size
		int width = image.getWidth();
		int height = image.getHeight();
		
		// allocate memory for result
		FloatProcessor result = new FloatProcessor(width, height);
		
		// iterate over pixels
		float[] hsb = new float[3]; 
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int c = image.get(x, y);
				int r = (c & 0xFF0000) >> 16;
				int g = (c & 0xFF00) >> 8;
				int b =  c & 0xFF;
				Color.RGBtoHSB(r, g, b, hsb);
				result.setf(x, y, hsb[2]);
			}
		}
		
		return result;
	}
}
