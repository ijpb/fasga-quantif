/**
 * 
 */
package inra.ijpb.appli.fasga;

import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.Color;

/**
 * Some color conversion utilities.
 * 
 * @author dlegland
 *
 */
public class ColorUtils {
	/**
	 * Computes the hue of a RGB image and returns the result in a
	 * FloatProcessor.
	 */
	public static final FloatProcessor computeHue(ColorProcessor image)
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
	
	/**
	 * Compute luma component of a color image, as weighted sum of RGB
	 * components, and returns the result in a float processor instead of a
	 * ByteProcessor.
	 * 
	 */
	public static final FloatProcessor computeLuma(ColorProcessor image)
	{
		// get image size
		int width = image.getWidth();
		int height = image.getHeight();
		
		// allocate memory for result
		FloatProcessor result = new FloatProcessor(width, height);
		
		// iterate over pixels
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int c = image.get(x, y);
				int r = (c & 0xFF0000) >> 16;
				int g = (c & 0xFF00) >> 8;
				int b =  c & 0xFF;
				float luma = (r * .299f + g * .587f + b * .114f) / 255;
				result.setf(x, y, luma);
			}
		}
		
		return result;
	}
	
	public static final ColorProcessor colorizeLabelImage(
			ImageProcessor labelImage, int[][] rgbValues)	
	{
		int nColors = rgbValues.length;
		int[] colorCodes = new int[nColors];
		for (int i = 0; i < nColors; i++)
		{
			int[] rgb = rgbValues[i];
			colorCodes[i] =  getColorCode(rgb[0], rgb[1], rgb[2]);
		}
		
		return colorizeLabelImage(labelImage, colorCodes);
	}
	
	public static final ColorProcessor colorizeLabelImage(
			ImageProcessor labelImage, int[] colorCodes)	
	{
		int width = labelImage.getWidth();
		int height = labelImage.getHeight();
		ColorProcessor colorImage = new ColorProcessor(width, height);

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int label = labelImage.get(x, y);
				colorImage.set(x, y, colorCodes[label]);
			}
		} 
		
		return colorImage;
	}
	
	private static final int getColorCode(int r, int g, int b)
	{
		return (r & 0x00FF) << 16 | (g & 0x00FF) << 8 | (b & 0x00FF); 
	}
}
