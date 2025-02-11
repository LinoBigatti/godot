/*************************************************************************/
/*  GodotView.java                                                       */
/*************************************************************************/
/*                       This file is part of:                           */
/*                           GODOT ENGINE                                */
/*                      https://godotengine.org                          */
/*************************************************************************/
/* Copyright (c) 2007-2021 Juan Linietsky, Ariel Manzur.                 */
/* Copyright (c) 2014-2021 Godot Engine contributors (cf. AUTHORS.md).   */
/*                                                                       */
/* Permission is hereby granted, free of charge, to any person obtaining */
/* a copy of this software and associated documentation files (the       */
/* "Software"), to deal in the Software without restriction, including   */
/* without limitation the rights to use, copy, modify, merge, publish,   */
/* distribute, sublicense, and/or sell copies of the Software, and to    */
/* permit persons to whom the Software is furnished to do so, subject to */
/* the following conditions:                                             */
/*                                                                       */
/* The above copyright notice and this permission notice shall be        */
/* included in all copies or substantial portions of the Software.       */
/*                                                                       */
/* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,       */
/* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF    */
/* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.*/
/* IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY  */
/* CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,  */
/* TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE     */
/* SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.                */
/*************************************************************************/

package org.godotengine.godot;

import org.godotengine.godot.input.GodotGestureHandler;
import org.godotengine.godot.input.GodotInputHandler;
import org.godotengine.godot.utils.GLUtils;
import org.godotengine.godot.xr.XRMode;
import org.godotengine.godot.xr.ovr.OvrConfigChooser;
import org.godotengine.godot.xr.ovr.OvrContextFactory;
import org.godotengine.godot.xr.ovr.OvrWindowSurfaceFactory;
import org.godotengine.godot.xr.regular.RegularConfigChooser;
import org.godotengine.godot.xr.regular.RegularContextFactory;
import org.godotengine.godot.xr.regular.RegularFallbackConfigChooser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;

/**
 * A simple GLSurfaceView sub-class that demonstrate how to perform
 * OpenGL ES 2.0 rendering into a GL Surface. Note the following important
 * details:
 *
 * - The class must use a custom context factory to enable 2.0 rendering.
 *   See ContextFactory class definition below.
 *
 * - The class must use a custom EGLConfigChooser to be able to select
 *   an EGLConfig that supports 2.0. This is done by providing a config
 *   specification to eglChooseConfig() that has the attribute
 *   EGL10.ELG_RENDERABLE_TYPE containing the EGL_OPENGL_ES2_BIT flag
 *   set. See ConfigChooser class definition below.
 *
 * - The class must select the surface's format, then choose an EGLConfig
 *   that matches it exactly (with regards to red/green/blue/alpha channels
 *   bit depths). Failure to do so would result in an EGL_BAD_MATCH error.
 */
public class GodotView extends GLSurfaceView {
	private static String TAG = GodotView.class.getSimpleName();

	private final Godot godot;
	private final GodotInputHandler inputHandler;
	private final GestureDetector detector;
	private final GodotRenderer godotRenderer;

	private EGLConfigChooser eglConfigChooser;
	private EGLContextFactory eglContextFactory;
	private EGLContext eglSecondaryContext;

	public GodotView(Context context, Godot godot, XRMode xrMode, boolean p_use_gl3, boolean p_use_debug_opengl, boolean p_translucent) {
		super(context);
		GLUtils.use_gl3 = p_use_gl3;
		GLUtils.use_debug_opengl = p_use_debug_opengl;

		this.godot = godot;
		this.inputHandler = new GodotInputHandler(this);
		this.detector = new GestureDetector(context, new GodotGestureHandler(this));
		this.godotRenderer = new GodotRenderer();

		init(xrMode, p_translucent);
	}

	public void initInputDevices() {
		this.inputHandler.initInputDevices();
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		super.onTouchEvent(event);
		this.detector.onTouchEvent(event);
		return inputHandler.onTouchEvent(event);
	}

	@Override
	public boolean onKeyUp(final int keyCode, KeyEvent event) {
		return inputHandler.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onKeyDown(final int keyCode, KeyEvent event) {
		return inputHandler.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		return inputHandler.onGenericMotionEvent(event) || super.onGenericMotionEvent(event);
	}

	private void init(XRMode xrMode, boolean translucent) {
		setPreserveEGLContextOnPause(true);
		setFocusableInTouchMode(true);
		switch (xrMode) {
			case OVR:
			case OPENXR:
				// Replace the default egl config chooser.
				eglConfigChooser = new OvrConfigChooser();

				// Replace the default context factory.
				eglContextFactory = new OvrContextFactory();

				// Replace the default window surface factory.
				setEGLWindowSurfaceFactory(new OvrWindowSurfaceFactory());
				break;

			case REGULAR:
			default:
				/* By default, GLSurfaceView() creates a RGB_565 opaque surface.
				 * If we want a translucent one, we should change the surface's
				 * format here, using PixelFormat.TRANSLUCENT for GL Surfaces
				 * is interpreted as any 32-bit surface with alpha by SurfaceFlinger.
				 */
				if (translucent) {
					this.setZOrderOnTop(true);
					this.getHolder().setFormat(PixelFormat.TRANSLUCENT);
				}

				/* Setup the context factory for 2.0 rendering.
				 * See ContextFactory class definition below
				 */
				eglContextFactory = new RegularContextFactory();

				/* We need to choose an EGLConfig that matches the format of
				 * our surface exactly. This is going to be done in our
				 * custom config chooser. See ConfigChooser class definition
				 * below.
				 */

				eglConfigChooser =
						new RegularFallbackConfigChooser(8, 8, 8, 8, 24, 0,
								new RegularFallbackConfigChooser(8, 8, 8, 8, 16, 0,
										// Let such a desperate fallback be used if under some circumstances that's the best we can get
										// (the translucency flag would be ignored, but that's better than not running at all)
										new RegularConfigChooser(5, 6, 5, 0, 16, 0)));
				break;
		}
		setEGLConfigChooser(eglConfigChooser);
		setEGLContextFactory(eglContextFactory);

		/* Set the renderer responsible for frame rendering */
		setRenderer(godotRenderer);
	}

	public boolean createOffscreenGL() {
		EGL10 egl = (EGL10)EGLContext.getEGL();
		EGLConfig eglConfig = eglConfigChooser.chooseConfig(egl, egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY));
		eglSecondaryContext = eglContextFactory.createContext(egl, egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY), eglConfig);
		if (eglSecondaryContext == EGL10.EGL_NO_CONTEXT) {
			eglSecondaryContext = null;
		}
		return eglSecondaryContext != null;
	}

	public void setOffscreenGLCurrent(boolean p_current) {
		EGL10 egl = (EGL10)EGLContext.getEGL();
		egl.eglMakeCurrent(egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY), EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, p_current ? eglSecondaryContext : EGL10.EGL_NO_CONTEXT);
	}

	public void destroyOffscreenGL() {
		if (eglSecondaryContext != null) {
			EGL10 egl = (EGL10)EGLContext.getEGL();
			eglContextFactory.destroyContext(egl, egl.eglGetCurrentDisplay(), eglSecondaryContext);
			eglSecondaryContext = null;
		}
	}

	public void onBackPressed() {
		godot.onBackPressed();
	}

	public GodotInputHandler getInputHandler() {
		return inputHandler;
	}

	@Override
	public void onResume() {
		super.onResume();

		queueEvent(() -> {
			// Resume the renderer
			godotRenderer.onActivityResumed();
			GodotLib.focusin();
		});
	}

	@Override
	public void onPause() {
		super.onPause();

		queueEvent(() -> {
			GodotLib.focusout();
			// Pause the renderer
			godotRenderer.onActivityPaused();
		});
	}
}
