/**
 * 
 */
package inra.ijpb.appli.fasga;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/**
 * @author David Legland
 *
 */
public class ColorSpaceConverterPlugin implements PlugInFilter
{
	
    // Original image
    private ImagePlus  imagePlus;
    
    // Result stack
    private ImageStack resultStack;
	
	
//	// RGB colour space chosen
//    private String     RGBcolourspace;
    
    String[] colorSpaceNames = {
            "HSV/HSB", 
            "HSL"};


    // Destination colour space chosen
    private String     colorSpaceName;
    
//    // L* white reference
//    private float[]    Lwhite;
//    
//    // Convert to RGB color (8 bits per channel)
//    private boolean    toRGBcolor = true;
//    
//    // Display the full 16-bit range (0-65535)
//    private boolean    full16 = true;

 
	
    public int setup(String arg, ImagePlus imp){
		this.imagePlus = imp;
		if (arg.equals("about"))
		{
			showAbout();
			return DONE;
		}
		return DOES_ALL;
	}
  
	public void run(ImageProcessor image)
	{
		GenericDialog gd = showDialog();
		if (gd.wasCanceled())
			return;
		
		parseDialogParameters(gd);
		
		// Compute size of image
		int width = image.getWidth();
		int height = image.getHeight();
		int size = width * height;
		
		ColorSpace colorSpace;
		if (colorSpaceName.equals("HSV/HSB"))
			colorSpace = new ColorSpace.HSV();
		else if (colorSpaceName.equals("HSL"))
			colorSpace = new ColorSpace.HSL();
		else {
			IJ.error("Unknown ColorSpace", "Can not Process color space: "
					+ colorSpaceName);
			return;
		}

		int nChannels = colorSpace.getChannelNumber();
		float[][] channelData = new float[nChannels][size];
		
		float[] sRGB = new float[3];
		float[] newValues = new float[nChannels];
		
		// iterate over pixel values
		for (int i = 0; i < size; i++)
		{
			int rgb = image.get(i);
			
			sRGB[0] = ((rgb & 0xff0000) >> 16) / 255f;
			sRGB[1] = ((rgb & 0x00ff00) >> 8) / 255f;
			sRGB[2] = (rgb & 0x0000ff) / 255f;
			
			colorSpace.convertSRGB(sRGB, newValues);
			
			for (int c=0; c < nChannels; c++) 
			{
				channelData[c][i] = newValues[c];
			}
		}
		
		// create stack for storing result channels
		String newTitle = imagePlus.getShortTitle().replace(" (RGB)", "").concat(colorSpaceName);
		resultStack = new ImageStack(width, height);
		
		// Add each channel to the result
		String[] channelNames = colorSpace.getChannelNames(); 
		for (int c = 0; c < nChannels; c++)
		{
			ImageProcessor channel = new FloatProcessor(width, height);
			channel.setPixels(channelData[c]);
			
			resultStack.addSlice(channelNames[c], channel);

		}
		
		ImagePlus resultPlus = new ImagePlus(newTitle, resultStack);
		resultPlus.show();
		IJ.resetMinAndMax();
	}
	
    public GenericDialog showDialog(){    	
//	    String[] RGBcolourspaces = {"sRGB", 
//	                                "Adobe RGB", 
//	                                "ProPhoto RGB",
//	                                "eciRGB v2",
//	                                "Custom"};
	
//	    String[] colourspaces = {"XYZ", 
//                "Yxy", 
//                "YUV",
//                "YIQ",
//                "YCbCr",
//                "Luv", 
//                "Lab",        
//                "AC1C2",
//                "I1I2I3", 
//                "Yuv", 
//                "YQ1Q2", 
//                "HSI", 
//                "HSV/HSB", 
//                "HSL", 
//                "LCHLuv", 
//                "LSHLuv", 
//                "LSHLab",
//                "CMYK",
//                "CMYK plates"};
	    GenericDialog gd = new GenericDialog("Color Space Converter");
//	    gd.addChoice("From color space:", RGBcolourspaces, RGBcolourspaces[0]);
	    gd.addChoice("To color space:", colorSpaceNames, colorSpaceNames[0]);
//	    gd.addCheckbox(" Convert images to RGB color (8 bpc)", toRGBcolor);
//	    gd.addCheckbox(" Display full 16-bit range (0-65535)", full16);
	    gd.showDialog();
	    return gd;
	}

    private void parseDialogParameters(GenericDialog gd) 
    {
//    	colourspace = colourspaces[gd.getNextChoiceIndex()];
//	    RGBcolourspace = RGBcolourspaces[gd.getNextChoiceIndex()];
	    colorSpaceName = colorSpaceNames[gd.getNextChoiceIndex()];
//	    toRGBcolor = gd.getNextBoolean();
//	    full16 = gd.getNextBoolean();
	}
    
    void showAbout() {
		IJ.showMessage("About Color Space Converter...",
				"Converts an RGB image into a new stack\nwith one slice per new channel.\n");
      } 

}
