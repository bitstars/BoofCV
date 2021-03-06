/*
 * Copyright (c) 2011-2013, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.tracker.circulant;

import boofcv.abst.transform.fft.DiscreteFourierTransform;
import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.alg.misc.PixelMath;
import boofcv.alg.transform.fft.DiscreteFourierTransformOps;
import boofcv.struct.image.ImageFloat64;
import boofcv.struct.image.ImageSingleBand;
import boofcv.struct.image.InterleavedF64;
import georegression.struct.shapes.Rectangle2D_F32;

/**
 * <p>
 * Tracker that uses the theory of Circulant matrices, Discrete Fourier Transform (DCF), and linear classifiers to track
 * a target and learn its changes in appearance [1].  The target is assumed to be rectangular and has fixed size and
 * location.  A dense local search is performed around the most recent target location.  The search is done quickly
 * using the DCF.
 * </p>
 *
 * <p>
 * Tracking is performed using texture information.  Since only one description of the target is saved, tracks can
 * drift over time.  Tracking performance seems to improve if the object has distinctive edges and that's included
 * in the track region.
 * </p>
 *
 * <p>
 * TODO note change from paper here
 * </p>
 *
 * <p>
 * [1] Henriques, Joao F., et al. "Exploiting the circulant structure of tracking-by-detection with kernels."
 * Computer Vision–ECCV 2012. Springer Berlin Heidelberg, 2012. 702-715.
 * </p>
 *
 * @author Peter Abeles
 */
// TODO Visualize tracker internal data
// TODO fix unit tests
public class CirculantTracker<T extends ImageSingleBand> {

	// --- Tuning parameters
	// spatial bandwidth (proportional to target)
	private double output_sigma_factor;

	// gaussian kernel bandwidth
	private double sigma;

	// regularization term
	private double lambda;
	// linear interpolation term.  Adjusts how fast it can learn
	private double interp_factor;

	// the maximum pixel value
	private double maxPixelValue;

	// extra padding around the selected region
	private double padding;

	//----- Internal variables
	// computes the FFT
	private DiscreteFourierTransform<ImageFloat64,InterleavedF64> fft = DiscreteFourierTransformOps.createTransformF64();

	// storage for subimage of input image
	protected ImageFloat64 templateNew = new ImageFloat64(1,1);
	// storage for the subimage of the previous frame
	protected ImageFloat64 template = new ImageFloat64(1,1);

	// cosine window used to reduce artifacts from FFT
	protected ImageFloat64 cosine = new ImageFloat64(1,1);

	// Storage for the kernel's response
	private ImageFloat64 k = new ImageFloat64(1,1);
	private InterleavedF64 kf = new InterleavedF64(1,1,2);

	// Learn values.  used to compute weight in linear classifier
	private InterleavedF64 alphaf = new InterleavedF64(1,1,2);
	private InterleavedF64 newAlphaf = new InterleavedF64(1,1,2);

	// location of target
	protected Rectangle2D_F32 regionTrack = new Rectangle2D_F32();
	protected Rectangle2D_F32 regionOut = new Rectangle2D_F32();

	// Used for computing the gaussian kernel
	protected ImageFloat64 gaussianWeight = new ImageFloat64(1,1);
	protected InterleavedF64 gaussianWeightDFT = new InterleavedF64(1,1,2);

	// detector response
	private ImageFloat64 response = new ImageFloat64(1,1);

	// storage for storing temporary results
	private ImageFloat64 tmpReal0 = new ImageFloat64(1,1);
	private ImageFloat64 tmpReal1 = new ImageFloat64(1,1);

	private InterleavedF64 tmpFourier0 = new InterleavedF64(1,1,2);
	private InterleavedF64 tmpFourier1 = new InterleavedF64(1,1,2);
	private InterleavedF64 tmpFourier2 = new InterleavedF64(1,1,2);

	private InterpolatePixelS<T> interp;

	private int workRegionSize;
	private float stepX,stepY;

	/**
	 * Configure tracker
	 *
	 * @param output_sigma_factor  spatial bandwidth (proportional to target) Try 1.0/16.0
	 * @param sigma Sigma for Gaussian kernel in linear classifier.  Try 0.2
	 * @param lambda Try 1e-2
	 * @param interp_factor Try 0.075
	 * @param padding
	 * @param workRegionSize
	 * @param maxPixelValue Maximum pixel value.  Typically 255
	 */
	public CirculantTracker(double output_sigma_factor, double sigma, double lambda, double interp_factor,
							double padding ,
							int workRegionSize ,
							double maxPixelValue,
							InterpolatePixelS<T> interp ) {
		this.output_sigma_factor = output_sigma_factor;
		this.sigma = sigma;
		this.lambda = lambda;
		this.interp_factor = interp_factor;
		this.maxPixelValue = maxPixelValue;
		this.interp = interp;

		this.padding = padding;
		this.workRegionSize = workRegionSize;

		resizeImages(workRegionSize);
		computeCosineWindow(cosine);
		computeGaussianWeights(workRegionSize);
	}

	/**
	 * Initializes tracking around the specified rectangle region
	 * @param image Image to start tracking from
	 * @param x0 top-left corner of region
	 * @param y0 top-left corner of region
	 * @param regionWidth region's width
	 * @param regionHeight region's height
	 */
	public void initialize( T image , int x0 , int y0 , int regionWidth , int regionHeight ) {

		if( image.width < regionWidth || image.height < regionHeight)
			throw new IllegalArgumentException("Track region is larger than input image");

		regionOut.width = regionWidth;
		regionOut.height = regionHeight;

		// adjust for padding
		int w = (int)(regionWidth*(1+padding));
		int h = (int)(regionHeight*(1+padding));
		int cx = x0 + regionWidth/2;
		int cy = y0 + regionHeight/2;

		// save the track location
		this.regionTrack.width = w;
		this.regionTrack.height = h;
		this.regionTrack.tl_x = cx-w/2;
		this.regionTrack.tl_y = cy-h/2;

		stepX = (w-1)/(float)(workRegionSize-1);
		stepY = (h-1)/(float)(workRegionSize-1);

		ensureInBounds(regionTrack,image.width,image.height);

		updateRegionOut();

		initialLearning(image);
	}


	/**
	 * Learn the target's appearance.
	 */
	protected void initialLearning( T image ) {
		// get subwindow at current estimated target position, to train classifier
		get_subwindow(image, template);

		// Kernel Regularized Least-Squares, calculate alphas (in Fourier domain)
		//	k = dense_gauss_kernel(sigma, x);
		dense_gauss_kernel(sigma, template, template,k);
		fft.forward(k, kf);

		// new_alphaf = yf ./ (fft2(k) + lambda);   %(Eq. 7)
		computeAlphas(gaussianWeightDFT, kf, lambda, alphaf);
	}

	/**
	 * Computes the cosine window
	 */
	protected static void computeCosineWindow( ImageFloat64 cosine ) {
		double cosX[] = new double[ cosine.width ];
		for( int x = 0; x < cosine.width; x++ ) {
			cosX[x] = 0.5*(1 - Math.cos( 2.0*Math.PI*x/(cosine.width-1) ));
		}
		for( int y = 0; y < cosine.height; y++ ) {
			int index = cosine.startIndex + y*cosine.stride;
			double cosY = 0.5*(1 - Math.cos( 2.0*Math.PI*y/(cosine.height-1) ));
			for( int x = 0; x < cosine.width; x++ ) {
				cosine.data[index++] = cosX[x]*cosY;
			}
		}
	}

	/**
	 * Computes the weights used in the gaussian kernel
	 */
	protected void computeGaussianWeights( int width ) {
		// desired output (gaussian shaped), bandwidth proportional to target size
		double output_sigma = Math.sqrt(width*width) * output_sigma_factor;

		double left = -0.5/(output_sigma*output_sigma);

		int radiusX = gaussianWeight.width/2;
		int radiusY = gaussianWeight.height/2;

		for( int y = 0; y < gaussianWeight.height; y++ ) {
			int index = gaussianWeight.startIndex + y*gaussianWeight.stride;

			double ry = y-radiusY;

			for( int x = 0; x < gaussianWeight.width; x++ ) {
				double rx = x-radiusX;

				gaussianWeight.data[index++] = Math.exp(left * (ry * ry + rx * rx));
			}
		}

		fft.forward(gaussianWeight,gaussianWeightDFT);
	}


	protected void resizeImages( int workRegionSize ) {
		templateNew.reshape(workRegionSize, workRegionSize);
		template.reshape(workRegionSize, workRegionSize);
		cosine.reshape(workRegionSize,workRegionSize);
		k.reshape(workRegionSize,workRegionSize);
		kf.reshape(workRegionSize,workRegionSize);
		alphaf.reshape(workRegionSize,workRegionSize);
		newAlphaf.reshape(workRegionSize,workRegionSize);
		response.reshape(workRegionSize,workRegionSize);
		tmpReal0.reshape(workRegionSize,workRegionSize);
		tmpReal1.reshape(workRegionSize,workRegionSize);
		tmpFourier0.reshape(workRegionSize,workRegionSize);
		tmpFourier1.reshape(workRegionSize,workRegionSize);
		tmpFourier2.reshape(workRegionSize,workRegionSize);
		gaussianWeight.reshape(workRegionSize,workRegionSize);
		gaussianWeightDFT.reshape(workRegionSize,workRegionSize);
	}

	/**
	 * Search for the track in the image and
	 *
	 * @param image Next image in the sequence
	 */
	public void performTracking( T image ) {
		updateTrackLocation(image);
		if( interp_factor != 0 )
			performLearning(image);
	}

	/**
	 * Find the target inside the current image by searching around its last known location
	 */
	protected void updateTrackLocation(T image) {
		get_subwindow(image, templateNew);

		// calculate response of the classifier at all locations
		// matlab: k = dense_gauss_kernel(sigma, x, z);
		dense_gauss_kernel(sigma, templateNew, template,k);

		fft.forward(k,kf);

		// response = real(ifft2(alphaf .* fft2(k)));   %(Eq. 9)
		DiscreteFourierTransformOps.multiplyComplex(alphaf, kf, tmpFourier0);
		fft.inverse(tmpFourier0, response);

		// find the pixel with the largest response
		int N = response.width*response.height;
		int indexBest = -1;
		double valueBest = -1;
		for( int i = 0; i < N; i++ ) {
			double v = response.data[i];
			if( v > valueBest ) {
				valueBest = v;
				indexBest = i;
			}
		}

		int peakX = indexBest % response.width;
		int peakY = indexBest / response.width;
		float offX=0,offY=0;

		// TODO try different techniques.  Average of a larger region?
//		float b = (float)tmpReal0.get(peakX,peakY);
//
//		if( peakX >= 1 && peakX < tmpReal0.width-1 ){
//			float a = (float)tmpReal0.get(peakX-1,peakY);
//			float c = (float)tmpReal0.get(peakX+1,peakY);
//			offX = FastHessianFeatureDetector.polyPeak(a, b, c);
//		}
//		if( peakY >= 1 && peakY < tmpReal0.height-1 ) {
//			float d = (float)tmpReal0.get(peakX,peakY-1);
//			float e = (float)tmpReal0.get(peakX,peakY+1);
//			offY = FastHessianFeatureDetector.polyPeak(d, b, e);
//		}

		// peak in region's coordinate system
		float deltaX = (peakX+offX) - templateNew.width/2;
		float deltaY = (peakY+offY) - templateNew.height/2;

//		System.out.printf("delts %7.5f %7.5f\n",deltaX,deltaY);

		// convert peak location into image coordinate system
		regionTrack.tl_x = regionTrack.tl_x + deltaX*stepX;
		regionTrack.tl_y = regionTrack.tl_y + deltaY*stepY;

		ensureInBounds(regionTrack,image.width,image.height);

		updateRegionOut();
	}

	private void updateRegionOut() {
		// add integer rounding?
		regionOut.tl_x = (regionTrack.tl_x+((int)regionTrack.width)/2)-((int)regionOut.width)/2;
		regionOut.tl_y = (regionTrack.tl_y+((int)regionTrack.height)/2)-((int)regionOut.height)/2;
	}

	/**
	 * Update the alphas and the track's appearance
	 */
	public void performLearning(T image) {
		// use the update track location
		get_subwindow(image, templateNew);

		// Kernel Regularized Least-Squares, calculate alphas (in Fourier domain)
		//	k = dense_gauss_kernel(sigma, x);
		dense_gauss_kernel(sigma, templateNew, templateNew, k);
		fft.forward(k,kf);

		// new_alphaf = yf ./ (fft2(k) + lambda);   %(Eq. 7)
		computeAlphas(gaussianWeightDFT, kf, lambda, newAlphaf);

		// subsequent frames, interpolate model
		// alphaf = (1 - interp_factor) * alphaf + interp_factor * new_alphaf;
		int N = alphaf.width*alphaf.height*2;
		for( int i = 0; i < N; i++ ) {
			alphaf.data[i] = (1-interp_factor)*alphaf.data[i] + interp_factor*newAlphaf.data[i];
		}

		// Set the previous image to be an interpolated version
		//		z = (1 - interp_factor) * z + interp_factor * new_z;
		N = templateNew.width* templateNew.height;
		for( int i = 0; i < N; i++ ) {
			template.data[i] = (1-interp_factor)* template.data[i] + interp_factor*templateNew.data[i];
		}
	}

	/**
	 * Gaussian Kernel with dense sampling.
	 *  Evaluates a gaussian kernel with bandwidth SIGMA for all displacements
	 *  between input images X and Y, which must both be MxN. They must also
	 *  be periodic (ie., pre-processed with a cosine window). The result is
	 *  an MxN map of responses.
	 *
	 * @param sigma Gaussian kernel bandwidth
	 * @param x Input image
	 * @param y Input image
	 * @param k Output containing Gaussian kernel for each element in target region
	 */
	public void dense_gauss_kernel( double sigma , ImageFloat64 x , ImageFloat64 y , ImageFloat64 k ) {

		InterleavedF64 xf=tmpFourier0,yf,xyf=tmpFourier2;
		ImageFloat64 xy = tmpReal0;
		double yy;

		// find x in Fourier domain
		fft.forward(x, xf);
		double xx = imageDotProduct(x);

		if( x != y ) {
			// general case, x and y are different
			yf = tmpFourier1;
			fft.forward(y,yf);
			yy = imageDotProduct(y);
		} else {
			// auto-correlation of x, avoid repeating a few operations
			yf = xf;
			yy = xx;
		}

		//----   xy = invF[ F(x)*F(y) ]
		// cross-correlation term in Fourier domain
		elementMultConjB(xf,yf,xyf);
		// convert to spatial domain
		fft.inverse(xyf,xy);
		circshift(xy,tmpReal1);

		// calculate gaussian response for all positions
		gaussianKernel(xx, yy, tmpReal1, sigma, k);
	}

	public static void circshift( ImageFloat64 a, ImageFloat64 b ) {
		int w2 = a.width/2;
		int h2 = b.height/2;

		for( int y = 0; y < a.height; y++ ) {
			int yy = (y+h2)%a.height;

			for( int x = 0; x < a.width; x++ ) {
				int xx = (x+w2)%a.width;

				b.set( xx , yy , a.get(x,y));
			}
		}

	}

	/**
	 * Computes the dot product of the image with itself
	 */
	public static double imageDotProduct(ImageFloat64 a) {

		double total = 0;

		int N = a.width*a.height;
		for( int index = 0; index < N; index++ ) {
			double value = a.data[index];
			total += value*value;
		}

		return total;
	}

	/**
	 * Element-wise multiplication of 'a' and the complex conjugate of 'b'
	 */
	public static void elementMultConjB( InterleavedF64 a , InterleavedF64 b , InterleavedF64 output ) {
		for( int y = 0; y < a.height; y++ ) {

			int index = a.startIndex + y*a.stride;

			for( int x = 0; x < a.width; x++, index += 2 ) {

				double realA = a.data[index];
				double imgA = a.data[index+1];
				double realB = b.data[index];
				double imgB = b.data[index+1];

				output.data[index] = realA*realB + imgA*imgB;
				output.data[index+1] = -realA*imgB + imgA*realB;
			}
		}
	}

	/**
	 * new_alphaf = yf ./ (fft2(k) + lambda);   %(Eq. 7)
	 */
	protected static void computeAlphas( InterleavedF64 yf , InterleavedF64 kf , double lambda ,
										 InterleavedF64 alphaf ) {

		for( int y = 0; y < kf.height; y++ ) {

			int index = yf.startIndex + y*yf.stride;

			for( int x = 0; x < kf.width; x++, index += 2 ) {
				double a = yf.data[index];
				double b = yf.data[index+1];

				double c = kf.data[index] + lambda;
				double d = kf.data[index+1];

				double bottom = c*c + d*d;

				alphaf.data[index] = (a*c + b*d)/bottom;
				alphaf.data[index+1] = (b*c - a*d)/bottom;
			}
		}
	}

	/**
	 * Computes the output of the Gaussian kernel for each element in the target region
	 *
	 * k = exp(-1 / sigma^2 * max(0, (xx + yy - 2 * xy) / numel(x)));
	 *
	 * @param xx ||x||^2
	 * @param yy ||y||^2
	 */
	protected static void gaussianKernel( double xx , double yy , ImageFloat64 xy , double sigma  , ImageFloat64 output ) {
		double sigma2 = sigma*sigma;
		double N = xy.width*xy.height;

		for( int y = 0; y < xy.height; y++ ) {
			int index = xy.startIndex + y*xy.stride;

			for( int x = 0; x < xy.width; x++ , index++ ) {

				// (xx + yy - 2 * xy) / numel(x)
				double value = (xx + yy - 2*xy.data[index])/N;

				double v = Math.exp(-Math.max(0, value) / sigma2);

				output.data[index] = v;
			}
		}
	}

	/**
	 * Copies the target into the output image and applies the cosine window to it.
	 */
	protected void get_subwindow( T image , ImageFloat64 output ) {

		// copy the target region

		interp.setImage(image);
		int index = 0;
		for( int y = 0; y < workRegionSize; y++ ) {
			float yy = regionTrack.tl_y + y*stepY;

			for( int x = 0; x < workRegionSize; x++ ) {
				float xx = regionTrack.tl_x + x*stepX;

				if( interp.isInFastBounds(xx,yy))
					output.data[index++] = interp.get_fast(xx,yy);
				else if( image.isInBounds((int)xx,(int)yy))
					output.data[index++] = interp.get(xx, yy);
				else
					output.data[index++] = 0;
			}
		}

		// normalize values to be from -0.5 to 0.5
		PixelMath.divide(output, maxPixelValue, output);
		PixelMath.plus(output, -0.5f, output);
		// apply the cosine window to it
		PixelMath.multiply(output,cosine,output);
	}

	/**
	 * Makes sure the specified region is inside the image bounds
	 */
	protected static void ensureInBounds( Rectangle2D_F32 region , int imgWidth , int imgHeight ) {
		if( region.tl_x < 0 )
			region.tl_x = 0;
		else if( region.tl_x > imgWidth-region.width )
			region.tl_x = imgWidth-region.width;
		if( region.tl_y < 0 )
			region.tl_y = 0;
		else if( region.tl_y > imgHeight-region.height )
			region.tl_y = imgHeight-region.height;
	}


	/**
	 * The location of the target in the image
	 */
	public Rectangle2D_F32 getTargetLocation() {
		return regionOut;
	}

	/**
	 * Visual appearance of the target
	 */
	public ImageFloat64 getTargetTemplate() {
		return template;
	}

	public ImageFloat64 getResponse() {
		return response;
	}
}
