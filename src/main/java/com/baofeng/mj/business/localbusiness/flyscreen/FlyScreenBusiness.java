package com.baofeng.mj.business.localbusiness.flyscreen;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.baofeng.mj.R;
import com.baofeng.mj.bean.BaseMessage;
import com.baofeng.mj.bean.DeviceInfo;
import com.baofeng.mj.bean.LocalVideoBean;
import com.baofeng.mj.bean.Login;
import com.baofeng.mj.bean.Resource;
import com.baofeng.mj.bean.Resource.PageItem;
import com.baofeng.mj.business.localbusiness.flyscreen.interfaces.FlyScreenListener;
import com.baofeng.mj.business.localbusiness.flyscreen.logic.FlyScreenLoginModel;
import com.baofeng.mj.business.localbusiness.flyscreen.logic.FlyScreenTcpSocket;
import com.baofeng.mj.business.localbusiness.flyscreen.logic.FlyScreenTcpSocket.HandlerCallBack;
import com.baofeng.mj.business.localbusiness.flyscreen.logic.FlyScreenUdpSocket;
import com.baofeng.mj.business.localbusiness.flyscreen.logic.TcpConnCallback;
import com.baofeng.mj.business.localbusiness.flyscreen.util.FileDirStack;
import com.baofeng.mj.business.localbusiness.flyscreen.util.FlyScreenConstant;
import com.baofeng.mj.business.localbusiness.flyscreen.util.FlyScreenUtil;
import com.baofeng.mj.business.localbusiness.flyscreen.util.ObjectToJsonString;
import com.baofeng.mj.business.publicbusiness.BaseApplication;
import com.baofeng.mj.business.spbusiness.SettingSpBusiness;
import com.baofeng.mj.ui.activity.FlyScreenHelpActivity;
import com.baofeng.mj.ui.activity.MediaGlActivity;
import com.baofeng.mj.ui.online.view.PlayerTypeChoseDialog;
import com.baofeng.mj.unity.IAndroidCallback;
import com.baofeng.mj.unity.UnityActivity;
import com.baofeng.mj.unity.UnityLocalBusiness;
import com.baofeng.mj.util.publicutil.Common;
import com.baofeng.mj.util.publicutil.NetworkUtil;
import com.baofeng.mj.util.viewutil.StartActivityHelper;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.storm.smart.common.utils.LogHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by zhaominglei on 2016/5/9.
 * 飞屏业务类
 */
public class FlyScreenBusiness implements HandlerCallBack, TcpConnCallback {

    private static FlyScreenBusiness instance;

    //尝试最大次数
    private static final int RETRY_MAX_TIME = 5;

    private static final int DELAY_SHORT_TIME = 1000;

    private static final int DELAY_LONG_TIME = 3000;

    private FlyScreenTcpSocket tcpClient;

    private FlyScreenUdpSocket udpSocket;

    private FlyScreenLoginModel loginModel;

    private DeviceInfo currDevInfo;
    private int currentDeviceServerPort;

    private FileDirStack fileDirStack = new FileDirStack();

    private HandlerThread mHandlerThread;

    private Handler mHandler;

    private Handler mMainThreadHandler;

    private FlyScreenListener mFlyScreenListener;
    private List<DeviceInfo> mDeviceInfos = new ArrayList<DeviceInfo>();;
    //尝试搜索次数
    private int mRetryTimes = 0;

    private static final int MSG_UPDATE_RES_DIR_PAGE = 0x1003;
    private static final int MSG_UPDATE_RES_NOTIFY_CHANGE = 0x1004;
    private static final int MSG_TCP_DISCONNECTED = 0x1005;
    private static final int MSG_RES_LOGIN_METHOD = 2;
    private static final int MSG_RES_LOGIN = 3;
    private static final int MSG_UPDATE_DIR_COUNT = 0x1006;
    public static final int EXCEPTION = 10;
    private FlyScreenBusiness() {
    }

    public static FlyScreenBusiness getInstance() {
        synchronized (FlyScreenBusiness.class) {
            if (instance == null) {
                instance = new FlyScreenBusiness();
            }
            return instance;
        }
    }

    public void setFlyScreenListener(FlyScreenListener flyScreenListener) {
        this.mFlyScreenListener = flyScreenListener;
    }

    public void init(final Context context) {
        tcpClient = FlyScreenTcpSocket.getSingleInstance();
        tcpClient.setDevListHandler(this);
        udpSocket = FlyScreenUdpSocket.getSingleInstance(context
                .getApplicationContext());
        loginModel = FlyScreenLoginModel.getSingleInstance(context
                .getApplicationContext());

        this.mHandlerThread = new HandlerThread("FlyScreenThread");
        this.mHandlerThread.start();
        this.mHandler = new Handler(this.mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {

                switch (msg.what) {
                    case FlyScreenConstant.FIND_FLY_SCREEN:
                        LogHelper.i("infos","=====in==FIND_FLY_SCREEN====1111111====");
                        mRetryTimes++;
                        addDevices();
                        LogHelper.i("infos","=====in==FIND_FLY_SCREEN====22222====");
                        if(BaseApplication.INSTANCE.getOrientationMode()) {
                            LogHelper.i("infos","=====in==FIND_FLY_SCREEN====3333===="+mFlyScreenListener);
                            if(mFlyScreenListener!= null)
                                mFlyScreenListener.onMessageReceived(FlyScreenConstant.FIND_FLY_SCREEN);
                        }
                        else{
                            LogHelper.i("infos","=====in==FIND_FLY_SCREEN====44444====");
                            List<DeviceInfo> devices = getmDeviceInfos();
                            if (devices == null && devices.size() == 0){
                                UnityLocalBusiness.sendFlyScreenDeviceList("");
                                LogHelper.i("infos","=====in==FIND_FLY_SCREEN====55555====");
                            } else{
                                UnityLocalBusiness.sendFlyScreenDeviceList(ObjectToJsonString.deviceToString(devices));
                                LogHelper.i("infos","=====in==FIND_FLY_SCREEN====66666666====");
                            }

                        }
                        if (mRetryTimes < RETRY_MAX_TIME) {
                            this.sendEmptyMessageDelayed(FlyScreenConstant.FIND_FLY_SCREEN, mRetryTimes < 3 ? DELAY_SHORT_TIME : DELAY_LONG_TIME);
                            LogHelper.i("infos","=====in==FIND_FLY_SCREEN====777777777777====");
                        } else {
                            LogHelper.i("infos","=====in==FIND_FLY_SCREEN====88888888888888====");
                            stopScan();
                            deviceNotFound();
                            LogHelper.i("infos","=====in==FIND_FLY_SCREEN====999999999999999====");
                        }
                        break;
                    case FlyScreenConstant.FLY_SCREEN_NOT_FOUND:
                        LogHelper.i("infos","=====in==FLY_SCREEN_NOT_FOUND====0000000000000000====");
                        if(BaseApplication.INSTANCE.getOrientationMode()){
                            LogHelper.i("infos","=====in==FLY_SCREEN_NOT_FOUND====1111111111===="+mFlyScreenListener);
                            if(mFlyScreenListener!= null)
                                mFlyScreenListener.onMessageReceived(FlyScreenConstant.FLY_SCREEN_NOT_FOUND);
                        }
                        else{
                            UnityLocalBusiness.sendFlyScreenDeviceList("");
                            LogHelper.i("infos","=====in==FLY_SCREEN_NOT_FOUND====22222222222222222====");
                        }
                        break;
                    case FlyScreenConstant.BACK_TO_DEVICE_LIST:
                        LogHelper.i("infos","=====in==BACK_TO_DEVICE_LIST====00000000000000====");
                        if(BaseApplication.INSTANCE.getOrientationMode()){
                            LogHelper.i("infos","=====in==BACK_TO_DEVICE_LIST====1111111===="+mFlyScreenListener);
                            if(mFlyScreenListener != null)
                                mFlyScreenListener.onMessageReceived(FlyScreenConstant.BACK_TO_DEVICE_LIST);
                        }
                        else{
                            LogHelper.i("infos","=====in==BACK_TO_DEVICE_LIST====2222222222====");
                            List<DeviceInfo> devices = getmDeviceInfos();
                            if (devices == null && devices.size() == 0){
                                LogHelper.i("infos","=====in==BACK_TO_DEVICE_LIST====33333333333====");
                                UnityLocalBusiness.sendFlyScreenDeviceList("");
                            } else{
                                UnityLocalBusiness.sendFlyScreenDeviceList(ObjectToJsonString.deviceToString(devices));
                                LogHelper.i("infos","=====in==BACK_TO_DEVICE_LIST====444444444444444====");
                            }

                        }
                        break;
                    default:break;
                }
            }
        };
        // TCP 资源请求回调
        this.mVideoHandlerCallBack = new VideoHandlerCallBack() {
            @Override
            public void handlerMessage(Message msg) {
                Resource.BasicResourceMessage basicResourceMessage = (Resource.BasicResourceMessage)msg.obj;
                switch (msg.what) {
                    case MSG_TCP_DISCONNECTED:// TCP 断开连接
                        // Android and Unity都需要
                        //Toast.makeText(context,"您的电脑已经关闭飞屏功能，页面即将调转 ?", 1500);
                        if(BaseApplication.INSTANCE.getOrientationMode()){
                            if(mFlyScreenListener!= null)
                                mFlyScreenListener.onMessageReceived(FlyScreenConstant.FLY_SCREEN_SERVERCLOSED);
                        }
                        else
                        {
                            UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_DEVICE_DISCONNECT);
                        }
                        break;
                    case MSG_UPDATE_RES_DIR_PAGE: // 更新目录资源
                        Resource.ResponsePageData responsePageData = null;
                        try {
                            responsePageData = Resource.ResponsePageData
                                    .parseFrom(basicResourceMessage.getDetailMsgBytes());
                        } catch (InvalidProtocolBufferException e) {
                            e.printStackTrace();
                        }
                        switch (responsePageData.getErrorcode()) {
                            case ResourceErrorCode_OK:
                                // 判断返回的数据是否是当前进入的目录
                                if (Common.valid(responsePageData.getUri(), true) && !fileDirStack.getCurDir().equals(responsePageData.getUri())) {
                                    return;
                                }
                                List<Resource.PageItem> pageItemList = responsePageData.getItemsList();
                                if (BaseApplication.INSTANCE.getOrientationMode()) {
                                    if (mFlyScreenListener != null)
                                        mFlyScreenListener.onDataReceived(FlyScreenConstant.GET_FILES_FROM_FLY_SCREEN, pageItemList);
                                } else {
                                    if (pageItemList == null || pageItemList.size() == 0)
                                        UnityLocalBusiness.sendFlyScreenDeviceResourceList("");
                                    else
                                        UnityLocalBusiness.sendFlyScreenDeviceResourceList(ObjectToJsonString.pageItemToString(pageItemList));
                                        FlyScreenUtil.saveSubtitleFile(pageItemList);
                                }
                                break;
                            case ResourceErrorCode_FAIL:
                                if (!fileDirStack.isEmpty()) {
                                    fileDirStack.back();
                                }
                                // 通知竖屏和横屏
                                if(BaseApplication.INSTANCE.getOrientationMode()){

                                    if (mFlyScreenListener != null)
                                        mFlyScreenListener.onMessageReceived(FlyScreenConstant.FLY_SCREEN_RESOURCE_FAIL);
                                }
                                else{
                                    UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_RESOURCE_FAIL);
                                }
                                break;
                            case ResourceErrorCode_FAIL_NotLogin:
                                if(BaseApplication.INSTANCE.getOrientationMode()){
                                    if (mFlyScreenListener != null)
                                        mFlyScreenListener.onMessageReceived(FlyScreenConstant.FLY_SCREEN_RESOURCE_FAIL_NOTLOGIN);
                                }
                                else{
                                    UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_RESOURCE_FAIL_NOTLOGIN);
                                }
                                break;
                            default:
                                break;
                        }
                        break;
                    case MSG_UPDATE_DIR_COUNT:
                        Resource.ResponseDirCount responseDirCount = null;
                        try {
                            responseDirCount = Resource.ResponseDirCount
                                    .parseFrom(basicResourceMessage.getDetailMsgBytes());
                        } catch (InvalidProtocolBufferException e) {
                        }
                        switch (responseDirCount.getErrorcode()) {
                            case ResourceErrorCode_OK:
//                                tcpClient.send(resListModel.createtDirPageRequest(
//                                        resType, getCurDirUri(), 0));
                                sendGetDirData();
                                break;
                            case ResourceErrorCode_FAIL:
                                //UnityPlayer.UnitySendMessage("AndroidInterface", "flyScreenContent", "");
                                if(BaseApplication.INSTANCE.getOrientationMode()){

                                    if (mFlyScreenListener != null)
                                        mFlyScreenListener.onMessageReceived(FlyScreenConstant.FLY_SCREEN_RESOURCE_FAIL);
                                }
                                else{
                                    UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_RESOURCE_FAIL);
                                }
                                break;
                            case ResourceErrorCode_FAIL_NotLogin:
                                if(BaseApplication.INSTANCE.getOrientationMode()){
                                    if (mFlyScreenListener != null)
                                        mFlyScreenListener.onMessageReceived(FlyScreenConstant.FLY_SCREEN_RESOURCE_FAIL_NOTLOGIN);
                                }
                                else{
                                    UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_RESOURCE_FAIL_NOTLOGIN);
                                }
                                break;
                            default:
                                break;
                        }
                        break;
                    case MSG_UPDATE_RES_NOTIFY_CHANGE:
                        Resource.NotifyResourceChange notifyResourceChange = null;
                        try {
                            notifyResourceChange = Resource.NotifyResourceChange
                                    .parseFrom(basicResourceMessage.getDetailMsgBytes());
                        } catch (InvalidProtocolBufferException e) {
                        }

                        //Logger.i("数据改变");
                        if (notifyResourceChange.getUri().equals("") && notifyResourceChange.getReason().getNumber() == 1) {
                            fileDirStack.clear();
                            requestLoginData(currDevInfo);
                        }
                        break;
                    default:break;
                }
            }
        };
    }

    public void setTcpReceiver(boolean isResume) {
        if (tcpClient != null) {
            tcpClient.registerVideoReceiver(isResume ? viedeoRecieve : null);
        }
    }

    /***
     * 开始扫描
     */
    public void startScan() {
        if (NetworkUtil.networkEnable()) {
            udpSocket.udpDeviceList();
            if(BaseApplication.INSTANCE.getOrientationMode()) {
                if (mFlyScreenListener != null)
                    mFlyScreenListener.onMessageReceived(FlyScreenConstant.SHOW_PROGRESS_BAR);
            }
            if(this.mHandler != null)
                this.mHandler.sendEmptyMessageDelayed(FlyScreenConstant.FIND_FLY_SCREEN, DELAY_SHORT_TIME);
        }else {
            if(mFlyScreenListener!= null)
                mFlyScreenListener.onMessageReceived(FlyScreenConstant.FLY_SCREEN_NETWORK_ERROR);
        }

    }

    /***
     * 停止扫描
     */
    private void stopScan() {
        mHandler.removeMessages(FlyScreenConstant.FIND_FLY_SCREEN);
        mRetryTimes = 0;
    }

    public void setMainThreadHandler(Handler handler) {
        this.mMainThreadHandler = handler;
    }

    public List<DeviceInfo> getDeviceList() {
        List<DeviceInfo> udpdevices = udpSocket.getDevList();
        return udpdevices;
    }

    @Override
    public void handlerMessage(Message msg) {
        switch (msg.what) {
            case 2:
                break;
            case 3:// 返回登录结果
                dealLoginResult(msg);
                break;
            case 4:
                break;
            case 0x1003://private static final int MSG_TCP_DISCONNECTED = 0x1003;
                if(BaseApplication.INSTANCE.getOrientationMode()){
                    if(mFlyScreenListener!= null)
                        mFlyScreenListener.onMessageReceived(FlyScreenConstant.FLY_SCREEN_SERVERCLOSED);
                }
                else
                {
                    UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_DEVICE_DISCONNECT);
                }
                 break;
            case EXCEPTION:
                LogHelper.e("infosss","===========EXCEPTION===========");
                UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_NETWORK_ERROR);
                break;
            default:break;
        }
    }

    protected void dealLoginResult(Message msg) {
        Login.LoginResultErrorcode r = loginModel.getLoginErrorCode();

        switch (r) {
            case LoginResultErrorcode_OK:
                if(BaseApplication.INSTANCE.getOrientationMode()){

                }
                else{
                    UnityLocalBusiness.sendFlyScreenServerPort(FlyScreenLoginModel.getServerPort()); // 登录成功发送端口号
                    currentDeviceServerPort = FlyScreenLoginModel.getServerPort();
                }
                if (FlyScreenLoginModel.isbContentEmpty()) {
                } else {
                    setFirstdir();
                }
                break;
            case LoginResultErrorcode_FAIL:
                if(BaseApplication.INSTANCE.getOrientationMode()){

                }
                else {
                    UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_LOGIN_FAIL);
                }
                break;
            case LoginResultErrorcode_BadUserNameOrPassWord://密码错误
                if(BaseApplication.INSTANCE.getOrientationMode()){
                    if(mFlyScreenListener!= null)
                        mFlyScreenListener.onMessageReceived(FlyScreenConstant.FLY_SCREEN_LOGIN_PWD_ERROR);
                }
                else{
                    UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_LOGIN_PASSWORD_ERROR);
                }
                 break;
            case LoginResultErrorcode_ExceedMaxUser:
                if(BaseApplication.INSTANCE.getOrientationMode()) {
                    if (mFlyScreenListener != null)
                        mFlyScreenListener.onMessageReceived(FlyScreenConstant.FLY_SCREEN_RECONNECT);
                }
                else{
                    UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_LOGIN_DEVICE_CONNECTED);
                }
                break;
            case LoginResultErrorcode_MethodNotSupported:
                if(BaseApplication.INSTANCE.getOrientationMode()){

                }
                else {
                    UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_LOGIN_METHOD_NOTSUPPORTED);
                }
                break;
            case LoginResultErrorcode_ProtoVerMisMatch:
                if(BaseApplication.INSTANCE.getOrientationMode()){

                }
                else {
                    UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_LOGIN_VERSION_NOTMATCHED);
                }
                break;
            default:break;
        }
    }

    private void setFirstdir() {
        fileDirStack.clear();
        fileDirStack.push("/");
        sendGetDirData();
    }

    public void onDestroy() {
        if(udpSocket!=null) {
            udpSocket.UdpClose();
        }
        if(tcpClient!= null) {
            tcpClient.close();
        }
    }

    private FlyScreenTcpSocket.resourceDealRecieve viedeoRecieve = new FlyScreenTcpSocket.resourceDealRecieve() {
        @Override
        public void handleVideoData(BaseMessage.MessageType mt, ByteString byteStr) {
            switch (mt) {
                case MessageType_Video: // 视频业务
                    try {
                        Resource.BasicResourceMessage baseResourceMsg = Resource.BasicResourceMessage
                                .parseFrom(byteStr);
                        if (baseResourceMsg.getMt() != Resource.ResourceMessageType.ResourceMessageType_ResponseHttpServerPort) {
                            updateResList(baseResourceMsg);
                        }
                    } catch (Exception e) {
                    }
                    break;
                case MessageType_Music: // 音乐业务
                    break;
                case MessageType_Game: // 游戏业务
                    break;
                case MessageType_Picture: // 图片业务
                    break;
                default:
                    return;
            }
        }
    };
    /*
    * 更新资源列表
     */
    public void updateResList(Resource.BasicResourceMessage baseResourceMsg) {
        if (baseResourceMsg == null)
            return;
        Message msg = null;
        switch (baseResourceMsg.getMt()) {
            case ResourceMessageType_ResponseDirPageData:
                msg = new Message();
                msg.what = MSG_UPDATE_RES_DIR_PAGE;
                msg.obj = baseResourceMsg;
                break;
            case ResourceMessageType_NotifyResourceChange:
                msg = new Message();
                msg.what = MSG_UPDATE_RES_NOTIFY_CHANGE;
                msg.obj = baseResourceMsg;
                break;
            case ResourceMessageType_ResponseDirCount:
                msg = new Message();
                msg.what = MSG_UPDATE_DIR_COUNT;
                msg.obj = baseResourceMsg;
                break;
            default:
                break;
        }
        mVideoHandlerCallBack.handlerMessage(msg);
    }
                // 应答特定目录下的[m,n)条数据
//                case ResourceMessageType_ResponseDirPageData:
//                    Resource.ResponsePageData responsePageData = Resource.ResponsePageData
//                            .parseFrom(baseResourceMsg.getDetailMsgBytes());
//                    if (responsePageData.getUri().equals(fileDirStack.getCurDir())) {
//                        if(mFlyScreenListener!= null)
//                        mFlyScreenListener.onDataReceived(FlyScreenConstant.GET_FILES_FROM_FLY_SCREEN,
//                                responsePageData.getItemsList());
//                        // Unity
//                        Message msg=Message.obtain();
//                        msg.what=FlyScreenConstant.GET_FILES_FROM_FLY_SCREEN;
//                        msg.obj = responsePageData.getItemsList();
//                        handlerMessage(msg);
//                    }
//                    break;
//                case ResourceMessageType_NotifyResourceChange:
//                case ResourceMessageType_ResponseDirCount:
//                    sendGetDirData();
//                    break;
//                default:
//                    break;
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    private void sendGetDirData() {
        FlyScreenUtil.sendVideoDataFromDir(tcpClient, fileDirStack.getCurDir());
    }

    public void requestLoginData(DeviceInfo deviceInfo) {
        stopScan();
        this.currDevInfo = deviceInfo;
        tcpClient.setmCurrDevInfo(currDevInfo);
        tcpClient.tcpConnectAndCallback(currDevInfo.getIp(),
                currDevInfo.getPort(), this);
    }

    public void reConnect(){
        if(! tcpClient.isTcpConnect(currDevInfo.getIp()) )
        {
            loginModel.requestLogout();
            loginModel.requestLogin(Login.LoginMethod.LoginMethod_None, "");
        }
//        if(! tcpClient.isTcpConnect(currDevInfo.getIp()) )
//        {
//            tcpClient.heartBeat();
//            tcpClient.reconn();
//            udpSocket.udpDeviceList();
//        }
    }

    @Override
    public void onTcpConnSuccess(DeviceInfo deviceInfo) {
        tcpClient.heartBeat();
        loginModel.requestLogout();
        loginModel.requestLogin(Login.LoginMethod.LoginMethod_None, "");
    }

    @Override
    public void onTcpConnError() {
        LogHelper.e("infosss","=======onTcpConnError=========="+ FlyScreenConstant.FLY_SCREEN_NETWORK_ERROR);
       UnityLocalBusiness.sendFlyScreenException(FlyScreenConstant.FLY_SCREEN_NETWORK_ERROR);

    }

    public void handlePageItem(PageItem pageItem, Context context) {
        if (validIsConnect()) {
            if (pageItem == null) {
                return;
            }
            if (pageItem.getBDir()) {
                fileDirStack.push(pageItem.getUri());
                sendGetDirData();
            } else {
                startPlay(pageItem, context);
            }
        }
    }
    // Unity:ForwardDir
    public void forwardDirectory(String path){
        if (validIsConnect()) {
                fileDirStack.push(path);
                sendGetDirData();
        }
    }
    // Unity:Current Directory
    public boolean getCurrentDirectory(){
        if(FlyScreenBusiness.getInstance().getFileDirStack().isEmpty()){
            return  false;
        }
        else {
            sendGetDirData();
            return  true;
        }
    }
    public FileDirStack getFileDirStack()
    {
        return fileDirStack;
    }

    private boolean validIsConnect() {
        if (tcpClient.isTcpConnect(currDevInfo.getIp())) {
            return true;
        } else {
            backToParentDir();
            return false;
        }
    }

    private void startPlay(PageItem pageItem, Context context) {
        if (pageItem == null || pageItem.getBDir()) {
            return;
        }

        showPlayerChooseDialog(context,pageItem);
    }



    /**
     * 播放模式选择
     */
    public void showPlayerChooseDialog(final Context context,final PageItem videoBean){
        int playerMode = SettingSpBusiness.getInstance().getPlayerMode();
        if(playerMode==0){//极简模式
            goToSimplePlay(context,videoBean);
        }else if(playerMode==1){//沉浸模式
            goToVRPlayer(context,videoBean);
        }else {
            PlayerTypeChoseDialog dialog = new PlayerTypeChoseDialog(context);
            dialog.setGoUnityParams(null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (R.id.player_choose_dialog_simple_layout == v.getId()){
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                goToSimplePlay(context,videoBean);
                            }
                        },80);


                    }else if(R.id.player_choose_dialog_vr_layout == v.getId()){
                        goToVRPlayer(context,videoBean);

                    }
                }
            });
            dialog.show();
        }
    }

    /**
     * 极简模式播放
     */
    private void goToSimplePlay(Context context,PageItem pageItem){
        final String videoName = stringFilter(pageItem.getName());
        final String path = FlyScreenUtil.getResUri(
                FlyScreenUtil.urlEncode(pageItem.getUri()), FlyScreenBusiness.getInstance().getCurrentDevice());

        final boolean hasSub = pageItem.getSubtitleType()>0 ?true:false; //是否有字幕文件
        Intent intent = new Intent(context, MediaGlActivity.class);
        intent.putExtra("videoPath", path);
        intent.putExtra("videoName", videoName);
        intent.putExtra("hasSub",hasSub);
        context.startActivity(intent);
    }

    /**
     * 沉浸模式播放
     */
    private void goToVRPlayer(Context context,PageItem pageItem){
        final String path = FlyScreenUtil.getResUri(
                FlyScreenUtil.urlEncode(pageItem.getUri()), FlyScreenBusiness.getInstance().getCurrentDevice());
        StartActivityHelper.playVideoWithFlyScreen(context, pageItem.getName(), path);
    }


    public static String stringFilter(String str) {
        str = str.replaceAll("【", "[").replaceAll("】", "]")
                .replaceAll("！", "!").replaceAll("：", ":");// 替换中文标号
        String regEx = "[『』]"; // 清除掉特殊字符
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(str);
        return m.replaceAll("").trim();
    }


    public DeviceInfo getCurrentDevice() {
        return currDevInfo;
    }

    private void deviceNotFound() {
        if (getmDeviceInfos().size() <= 0) {
            mHandler.sendEmptyMessage(FlyScreenConstant.FLY_SCREEN_NOT_FOUND);
//            if(mFlyScreenListener!= null)
//                mFlyScreenListener.onMessageReceived(FlyScreenConstant.FLY_SCREEN_NOT_FOUND);
//            Message message =Message.obtain();
//            message.what = FlyScreenConstant.FLY_SCREEN_NOT_FOUND;
//            handlerMessage(message);
        }
    }

    /***
     * 飞屏帮助页面
     * @param context
     */
    public void startFlyScreenHelpActivity(Context context) {
        Intent intent = new Intent(context, FlyScreenHelpActivity.class);
        context.startActivity(intent);
    }

    /***
     * 设置跳过引导页
     */
    public void setSkipGuide(boolean isSkip) {

        SettingSpBusiness.getInstance().setFlyScreenSkipGuide(isSkip);
    }

    /***
     * 获取是否跳过引导页
     * @return
     */

    public boolean isSkipGuide() {
        return SettingSpBusiness.getInstance().getFlyScreenSkipGuide();
    }
    public boolean isBeginStepGuide() {
        return SettingSpBusiness.getInstance().getFlyScreenBeginStepGuide();
    }
    public void resetBeginStepGuide(){
        SettingSpBusiness.getInstance().setFlyScreenBeginStepGuide(false);
    }

    /***
     * 返回上层目录
     */
    public void backToParentDir(){
        if (fileDirStack.back()) {
            sendGetDirData();
        } else {
            mHandler.sendEmptyMessage(FlyScreenConstant.BACK_TO_DEVICE_LIST);
        }
    }

    public List<DeviceInfo> getmDeviceInfos() {
        //addDevices();
        return mDeviceInfos;
    }

    private void addDevices() {
        List<DeviceInfo> udpdevices = FlyScreenBusiness.getInstance().getDeviceList();
        mDeviceInfos.clear();
        if(udpdevices == null || udpdevices.size() == 0)
            return;
        mDeviceInfos.addAll(udpdevices);
        if(mDeviceInfos.size()>0)
            Common.removeDuplicateWithOrder(mDeviceInfos);
    }

    public int getCurrentDeviceServerPort() {
        return currentDeviceServerPort;
    }
    public DeviceInfo getCurrDevInfo(){
        return currDevInfo;
    }
    //---------------------- 视频处理回调 --------------------------------
    public interface VideoHandlerCallBack {
        public void handlerMessage(Message msg);
    }
    private static VideoHandlerCallBack mVideoHandlerCallBack;
    public void setHandlerCallBack(VideoHandlerCallBack mVideoHandlerCallBack) {
        FlyScreenBusiness.mVideoHandlerCallBack = mVideoHandlerCallBack;
    }
    //------------------------- 结束 ------------------------------------
}