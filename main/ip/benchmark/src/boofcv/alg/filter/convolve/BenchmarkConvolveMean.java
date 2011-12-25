/*
 * Copyright (c) 2011, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://www.boofcv.org).
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

package boofcv.alg.filter.convolve;

import boofcv.abst.filter.blur.BlurFilter;
import boofcv.alg.filter.blur.BlurImageOps;
import boofcv.alg.filter.convolve.down.ConvolveDownNoBorderStandard;
import boofcv.alg.filter.convolve.noborder.ImplConvolveMean;
import boofcv.alg.misc.ImageTestingOps;
import boofcv.factory.filter.blur.FactoryBlurFilter;
import boofcv.factory.filter.kernel.FactoryKernel;
import boofcv.factory.filter.kernel.FactoryKernelGaussian;
import boofcv.misc.PerformerBase;
import boofcv.misc.ProfileOperation;
import boofcv.struct.convolve.Kernel1D_F32;
import boofcv.struct.convolve.Kernel1D_I32;
import boofcv.struct.convolve.Kernel2D_F32;
import boofcv.struct.convolve.Kernel2D_I32;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageSInt16;
import boofcv.struct.image.ImageSInt32;
import boofcv.struct.image.ImageUInt8;
import com.google.caliper.Param;
import com.google.caliper.Runner;
import com.google.caliper.SimpleBenchmark;

import java.util.Random;

/**
 * Benchmark for different convolution operations.
 * @author Peter Abeles
 */
public class BenchmarkConvolveMean extends SimpleBenchmark {
	static int width = 640;
	static int height = 480;
	static long TEST_TIME = 1000;
	static Random rand = new Random(234);

	static Kernel1D_F32 kernelF32;
	static ImageFloat32 input_F32 = new ImageFloat32(width,height);
	static ImageFloat32 out_F32 = new ImageFloat32(width,height);
	static ImageFloat32 storageF32 = new ImageFloat32(width,height);
	static Kernel1D_I32 kernelI32;
	static ImageUInt8 input_I8 = new ImageUInt8(width,height);
	static ImageSInt16 input_I16 = new ImageSInt16(width,height);
	static ImageUInt8 out_I8 = new ImageUInt8(width,height);

	BlurFilter<ImageFloat32> filter;

	// iterate through different sized kernel radius
	@Param({"1", "2", "3", "5","10"}) private int radius;

	public BenchmarkConvolveMean() {
		ImageTestingOps.randomize(input_I8,rand,0,20);
		ImageTestingOps.randomize(input_I16,rand,0,20);
		ImageTestingOps.randomize(input_F32,rand,0,20);
	}

	@Override protected void setUp() throws Exception {
		filter = FactoryBlurFilter.mean(ImageFloat32.class,radius);
		kernelF32 = FactoryKernel.table1D_F32(radius, true);
		kernelI32 = FactoryKernel.table1D_I32(radius);
	}

	public int timeConvolve_Vertical_U8_I8(int reps) {
		for( int i = 0; i < reps; i++ )
			ConvolveImageNoBorder.vertical(kernelI32, input_I8,out_I8,radius*2+1,false);
		return 0;
	}

	public int timeConvolve_Horizontal_U8_I8(int reps) {
		for( int i = 0; i < reps; i++ )
			ConvolveImageNoBorder.horizontal(kernelI32, input_I8, out_I8, radius * 2 + 1, false);
		return 0;
	}

	public int timeMean_U8_I8_Vertical(int reps) {
		for( int i = 0; i < reps; i++ )
			ImplConvolveMean.vertical(input_I8, out_I8, radius, false);
		return 0;
	}

	public int timeMean_F32_F32_Vertical(int reps) {
		for( int i = 0; i < reps; i++ )
			ImplConvolveMean.vertical(input_F32,out_F32,radius,false);
		return 0;
	}

	public int timeMean_F32_F32_Horizontal(int reps) {
		for( int i = 0; i < reps; i++ )
			ImplConvolveMean.horizontal(input_F32, out_F32, radius, false);
		return 0;
	}

	public int timeMean_F32_F32_Blur(int reps) {
		for( int i = 0; i < reps; i++ )
			BlurImageOps.mean(input_F32, out_F32, radius, storageF32);
		return 0;
	}

	public int timeMean_F32_F32_BlurAbst(int reps) {
		for( int i = 0; i < reps; i++ )
			filter.process(input_F32, out_F32);
		return 0;
	}

	public static void main( String args[] ) {
		System.out.println("=========  Profile Image Size "+ width +" x "+ height +" ==========");

		Runner.main(BenchmarkConvolveMean.class, args);
	}
}
