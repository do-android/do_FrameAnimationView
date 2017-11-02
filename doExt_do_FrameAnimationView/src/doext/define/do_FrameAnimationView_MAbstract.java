package doext.define;

import core.object.DoUIModule;
import core.object.DoProperty;
import core.object.DoProperty.PropertyDataType;


public abstract class do_FrameAnimationView_MAbstract extends DoUIModule{

	protected do_FrameAnimationView_MAbstract() throws Exception {
		super();
	}
	
	/**
	 * 初始化
	 */
	@Override
	public void onInit() throws Exception{
        super.onInit();
        //注册属性
		this.registProperty(new DoProperty("gifSource", PropertyDataType.String, "", false));
		this.registProperty(new DoProperty("imagesSource", PropertyDataType.String, "", false));
	}
}