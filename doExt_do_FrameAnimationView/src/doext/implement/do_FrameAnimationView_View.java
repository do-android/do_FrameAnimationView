package doext.implement;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.ImageView;
import core.helper.DoIOHelper;
import core.helper.DoImageLoadHelper;
import core.helper.DoJsonHelper;
import core.helper.DoUIModuleHelper;
import core.interfaces.DoIScriptEngine;
import core.interfaces.DoIUIModuleView;
import core.object.DoInvokeResult;
import core.object.DoUIModule;
import doext.define.do_FrameAnimationView_IMethod;
import doext.define.do_FrameAnimationView_MAbstract;
import doext.frameAnimationView.GIFHelper;

/**
 * 自定义扩展UIView组件实现类，此类必须继承相应VIEW类，并实现DoIUIModuleView,
 * do_FrameAnimationView_IMethod接口； #如何调用组件自定义事件？可以通过如下方法触发事件：
 * this.model.getEventCenter().fireEvent(_messageName, jsonResult);
 * 参数解释：@_messageName字符串事件名称，@jsonResult传递事件参数对象； 获取DoInvokeResult对象方式new
 * DoInvokeResult(this.model.getUniqueKey());
 */
public class do_FrameAnimationView_View extends ImageView implements DoIUIModuleView, do_FrameAnimationView_IMethod {

	/**
	 * 每个UIview都会引用一个具体的model实例；
	 */
	private do_FrameAnimationView_MAbstract model;
	private GIFHelper gifHelper;
	private MyTimerTask mGifTask;
	private MyTimerTask mImagesTask;

	private File cacheDiar;
	private Context mContext;

	public do_FrameAnimationView_View(Context context) {
		super(context);
		this.mContext = context;
		cacheDiar = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
	}

	/**
	 * 初始化加载view准备,_doUIModule是对应当前UIView的model实例
	 */
	@Override
	public void loadView(DoUIModule _doUIModule) throws Exception {
		this.model = (do_FrameAnimationView_MAbstract) _doUIModule;
	}

	/**
	 * 动态修改属性值时会被调用，方法返回值为true表示赋值有效，并执行onPropertiesChanged，否则不进行赋值；
	 * 
	 * @_changedValues<key,value>属性集（key名称、value值）；
	 */
	@Override
	public boolean onPropertiesChanging(Map<String, String> _changedValues) {
		return true;
	}

	/**
	 * 属性赋值成功后被调用，可以根据组件定义相关属性值修改UIView可视化操作；
	 * 
	 * @_changedValues<key,value>属性集（key名称、value值）；
	 */
	@Override
	public void onPropertiesChanged(Map<String, String> _changedValues) {
		DoUIModuleHelper.handleBasicViewProperChanged(this.model, _changedValues);
	}

	/**
	 * 同步方法，JS脚本调用该组件对象方法时会被调用，可以根据_methodName调用相应的接口实现方法；
	 * 
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V），获取参数值使用API提供DoJsonHelper类；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public boolean invokeSyncMethod(String _methodName, JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		if ("startImages".equals(_methodName)) {
			this.startImages(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("startGif".equals(_methodName)) {
			this.startGif(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("stop".equals(_methodName)) {
			this.stop(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		return false;
	}

	/**
	 * 异步方法（通常都处理些耗时操作，避免UI线程阻塞），JS脚本调用该组件对象方法时会被调用， 可以根据_methodName调用相应的接口实现方法；
	 * 
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V），获取参数值使用API提供DoJsonHelper类；
	 * @_scriptEngine 当前page JS上下文环境
	 * @_callbackFuncName 回调函数名 #如何执行异步方法回调？可以通过如下方法：
	 *                    _scriptEngine.callback(_callbackFuncName,
	 *                    _invokeResult);
	 *                    参数解释：@_callbackFuncName回调函数名，@_invokeResult传递回调函数参数对象；
	 *                    获取DoInvokeResult对象方式new
	 *                    DoInvokeResult(this.model.getUniqueKey());
	 */
	@Override
	public boolean invokeAsyncMethod(String _methodName, JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) {
		return false;
	}

	/**
	 * 释放资源处理，前端JS脚本调用closePage或执行removeui时会被调用；
	 */
	@Override
	public void onDispose() {
		if (mGifTask != null) {
			mGifTask.cancel();
			mGifTask = null;
		}
		if (mImagesTask != null) {
			mImagesTask.cancel();
			mImagesTask = null;
		}
		if (gifHelper != null) {
			gifHelper.resetFrame();
		}

		for (Map.Entry<String, MyAnimationDrawable> _entry : drawables.entrySet()) {
			if (_entry != null) {
				_entry.getValue().stop();
			}
		}

		AnimationDrawable _drawable = (AnimationDrawable) this.getDrawable();
		if (_drawable != null) {
			_drawable.stop();
			_drawable = null;
		}
	}

	/**
	 * 重绘组件，构造组件时由系统框架自动调用；
	 * 或者由前端JS脚本调用组件onRedraw方法时被调用（注：通常是需要动态改变组件（X、Y、Width、Height）属性时手动调用）
	 */
	@Override
	public void onRedraw() {
		this.setLayoutParams(DoUIModuleHelper.getLayoutParams(this.model));
	}

	/**
	 * 获取当前model实例
	 */
	@Override
	public DoUIModule getModel() {
		return model;
	}

	/**
	 * 开始播放；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public void startImages(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		int _repeat = DoJsonHelper.getInt(_dictParas, "repeat", 1);
		JSONArray _images = DoJsonHelper.getJSONArray(_dictParas, "data");
		if (_images == null || _images.length() <= 0)
			throw new Exception("startImages  data 不能为空！");
		AnimationDrawable _drawable = new AnimationDrawable();
		int totalDuration = 0; // 总动画执行时间
		for (int i = 0; i < _images.length(); i++) {
			JSONObject _obj = _images.getJSONObject(i);
			String _path = DoJsonHelper.getString(_obj, "path", null);
			if (_path == null)
				continue;
			_path = DoIOHelper.getLocalFileFullPath(this.model.getCurrentPage().getCurrentApp(), _path);
			if (!DoIOHelper.existFile(_path))
				continue;
			Bitmap _bmp = DoImageLoadHelper.getInstance().loadLocal(_path, -1, -1);
			if (_bmp == null)
				continue;
			int _duration = DoJsonHelper.getInt(_obj, "duration", 200);
			totalDuration += _duration;
			_drawable.addFrame(new BitmapDrawable(_bmp), _duration);
		}
		mImagesTask = new MyTimerTask(_repeat);
		startAnim(mImagesTask, _repeat, totalDuration, _drawable);
	}

	private Map<String, MyAnimationDrawable> drawables = new HashMap<String, MyAnimationDrawable>();

	private class MyAnimationDrawable {
		int totalDuration;
		AnimationDrawable drawable;

		public MyAnimationDrawable(AnimationDrawable _drawable, int _totalDuration) {
			this.drawable = _drawable;
			this.totalDuration = _totalDuration;

		}

		public void stop() {
			if (drawable != null) {
				drawable.stop();
				drawable = null;
			}
		}
	}

	@Override
	public void startGif(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		final int _repeat = DoJsonHelper.getInt(_dictParas, "repeat", 1);
		String _data = DoJsonHelper.getString(_dictParas, "data", null);
		if (TextUtils.isEmpty(_data))
			throw new Exception("startGif  data 不能为空！");
		final String _gifSource = DoIOHelper.getLocalFileFullPath(this.model.getCurrentPage().getCurrentApp(), _data);
		if (!DoIOHelper.existFile(_gifSource))
			throw new Exception("文件路径不存在");
		// 如果内存里面发现有这个gif图片就从内存里面取
		if (drawables.containsKey(_gifSource)) {

			MyAnimationDrawable _drawable = drawables.get(_gifSource);
			if (null != _drawable) {
				mGifTask = new MyTimerTask(_repeat);
				startAnim(mGifTask, _repeat, _drawable.totalDuration, _drawable.drawable);
				return;
			}
		}
		String _fileName = DoIOHelper.getFileNameWithoutExtension(_data);
		final File _file = new File(cacheDiar, _fileName);
		final File _journalFile = new File(_file, _fileName + ".journal");
		if (_file.exists() && _journalFile.exists()) {
			StringBuffer _sb = new StringBuffer();
			BufferedReader _br = new BufferedReader(new FileReader(_journalFile));
			String _str;
			while ((_str = _br.readLine()) != null) {
				_sb.append(_str);
			}
			_br.close();

			JSONArray _array = new JSONArray(_sb.toString());
			AnimationDrawable _drawable = new AnimationDrawable();
			int totalDuration = 0; // 总动画执行时间
			for (int i = 0; i < _array.length(); i++) {
				JSONObject _obj = _array.getJSONObject(i);
				String _key = DoJsonHelper.getAllKeys(_obj).get(0);
				int _duration = _obj.getInt(_key);
				totalDuration += _duration;
				_drawable.addFrame(new BitmapDrawable(_key), _duration);
			}

			MyAnimationDrawable _myDrawable = new MyAnimationDrawable(_drawable, totalDuration);
			drawables.put(_gifSource, _myDrawable);
			mGifTask = new MyTimerTask(_repeat);
			startAnim(mGifTask, _repeat, totalDuration, _drawable);
			return;
		}

		_file.mkdir();
		_journalFile.createNewFile();
		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				BufferedWriter bw = null;
				try {
					gifHelper = new GIFHelper(_file);
					if (DoIOHelper.isAssets(_gifSource)) {
						gifHelper.read(mContext.getAssets().open(DoIOHelper.getAssetsRelPath(_gifSource)));
					} else {
						gifHelper.read(new FileInputStream(new File(_gifSource)));
					}
					bw = new BufferedWriter(new FileWriter(_journalFile));
					bw.write(gifHelper.getJournalInfo());
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					try {
						if (null != bw) {
							bw.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				super.onPostExecute(result);
				AnimationDrawable _drawable = new AnimationDrawable();
				int totalDuration = 0; // 总动画执行时间
				for (int i = 0; i < gifHelper.getFrameCount(); i++) {
					Drawable drawable = new BitmapDrawable(gifHelper.getFrame(i));
					int _duration = gifHelper.getDelay(i);
					totalDuration += _duration;
					_drawable.addFrame(drawable, _duration);
				}

				MyAnimationDrawable _myDrawable = new MyAnimationDrawable(_drawable, totalDuration);
				drawables.put(_gifSource, _myDrawable);
				mGifTask = new MyTimerTask(_repeat);
				startAnim(mGifTask, _repeat, totalDuration, _drawable);
			}

		}.execute();

	}

	private void startAnim(MyTimerTask _task, int _repeat, int totalDuration, AnimationDrawable _drawable) {
		if (_repeat != 0 && _drawable != null) {
			this.setImageDrawable(_drawable);
			if (_repeat < 0) {
				_drawable.setOneShot(false); // 重复播放
				start();
			} else {
				_drawable.setOneShot(true); // 只会播放一次
				if (_repeat == 1) {
					start();
				} else {
					Timer _timer = new Timer();
					_timer.scheduleAtFixedRate(_task, 0, totalDuration);
				}
			}
		}

	}

	private class MyTimerTask extends TimerTask {

		private int count = 1;
		private int maxCount;

		private MyTimerTask(int _maxCount) {
			this.maxCount = _maxCount;
		}

		@Override
		public void run() {
			if (count == maxCount) {
				this.cancel();
			}
			handler.sendEmptyMessage(0);
			count++;
		}
	}

	@SuppressLint("HandlerLeak")
	private Handler handler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			start();
		};
	};

	/**
	 * 结束动画；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public void stop(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		AnimationDrawable _drawable = (AnimationDrawable) this.getDrawable();
		stop(_drawable);
	}

	private void start() {
		AnimationDrawable _drawable = (AnimationDrawable) this.getDrawable();
		if (_drawable != null) {
			_drawable.stop();
			_drawable.start();
		}
	}

	private void stop(AnimationDrawable _drawable) {
		if (mGifTask != null) {
			mGifTask.cancel();
			mGifTask = null;
		}
		if (mImagesTask != null) {
			mImagesTask.cancel();
			mImagesTask = null;
		}
		if (_drawable != null && _drawable.isRunning()) {
			_drawable.stop();
		}
	}

}