package com.fangwolf.GPrinterDemo;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.gprinter.aidl.GpService;
import com.gprinter.command.EscCommand;
import com.gprinter.command.GpCom;
import com.gprinter.command.GpUtils;
import com.gprinter.command.LabelCommand;
import com.gprinter.io.GpDevice;
import com.gprinter.io.PortParameters;
import com.gprinter.service.GpPrintService;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;


/**
 * @Auther 獠牙血狼
 * @Date 2019/3/20
 * @Desc 打印机工具
 */
public class PrintUtil {
    private static Context context;
    private static PortParameters mPortParam = new PortParameters();
    private static GpService mGpService = null;
    private static PrinterServiceConnection conn = null;
    private int mPrinterIndex = 0;
    private int mTotalCopies = 0;
    private static final int MAIN_QUERY_PRINTER_STATUS = 0xfe;
    private static final int REQUEST_PRINT_LABEL = 0xfd;
    private static final int REQUEST_PRINT_RECEIPT = 0xfc;

    public static class PrintUtilInstance {
        private static final PrintUtil INSTANCE = new PrintUtil();
    }

    public PrintUtil() {
    }

    public PrintUtil(Context context) {
        this.context = context;

        conn = new PrinterServiceConnection();
        Intent intent = new Intent(context, GpPrintService.class);
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE);

        registerReceiver();

        getUsbDeviceList();
        mPortParam.setIpAddr("192.168.123.100");
        mPortParam.setPortNumber(9100);
        Log.e("wolf", "\n---------------------------------------------------------\n" +
                "打印机USB名：" + mPortParam.getUsbDeviceName() +
                "\n---------------------------------------------------------");
    }

    public static PrintUtil getInstance() {
        return PrintUtil.PrintUtilInstance.INSTANCE;
    }

    /**
     * 打印机服务
     */
    private class PrinterServiceConnection implements ServiceConnection {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e("wolf", "Service-Disconnected >_<");
            mGpService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e("wolf", "Service-Connected ^_^");
            mGpService = GpService.Stub.asInterface(service);
            connectOrDisConnectToDevice();
        }
    }

    /**
     * usb列表
     */
    private void getUsbDeviceList() {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        // 获取连接的USB设备列表
        HashMap<String, UsbDevice> devices = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = devices.values().iterator();
        int count = devices.size();
        Log.e("wolf", "USB设备个数：" + count);
        if (count > 0) {
            while (deviceIterator.hasNext()) {
                UsbDevice device = deviceIterator.next();
                String devicename = device.getDeviceName();
                if (checkUsbDevicePidVid(device)) {
                    mPortParam.setUsbDeviceName(devicename);
                    mPortParam.setPortType(PortParameters.USB);
                }
            }
        } else {
            Log.e("wolf", "没有USB设备");
        }
    }

    /**
     * 判断打印机
     */
    boolean checkUsbDevicePidVid(UsbDevice dev) {
        int pid = dev.getProductId();
        int vid = dev.getVendorId();
        boolean rel = false;
        if ((vid == 34918 && pid == 256) || (vid == 1137 && pid == 85)
                || (vid == 6790 && pid == 30084)
                || (vid == 26728 && pid == 256) || (vid == 26728 && pid == 512)
                || (vid == 26728 && pid == 256) || (vid == 26728 && pid == 768)
                || (vid == 26728 && pid == 1024) || (vid == 26728 && pid == 1280)
                || (vid == 26728 && pid == 1536)) {
            rel = true;
        }
        return rel;
    }

    /**
     * 连接打印机
     */
    public void connectOrDisConnectToDevice() {
        int rel = 0;
        Log.e("wolf", "连接打印机:" + String.valueOf(mPortParam.getPortOpenState()));
        if (mPortParam.getPortOpenState() == false) {
            if (CheckPortParamters(mPortParam)) {
                try {
                    mGpService.closePort(0);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                switch (mPortParam.getPortType()) {
                    case PortParameters.USB:
                        try {
                            rel = mGpService.openPort(0, mPortParam.getPortType(), mPortParam.getUsbDeviceName(), 0);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        break;
                    case PortParameters.ETHERNET:
                        try {
                            rel = mGpService.openPort(0, mPortParam.getPortType(), mPortParam.getIpAddr(), mPortParam.getPortNumber());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        break;
                    case PortParameters.BLUETOOTH:
                        try {
                            rel = mGpService.openPort(0, mPortParam.getPortType(), mPortParam.getBluetoothAddr(), 0);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        break;
                }
                GpCom.ERROR_CODE r = GpCom.ERROR_CODE.values()[rel];
                Log.e("wolf", "连接打印机result :" + String.valueOf(r));
                if (r != GpCom.ERROR_CODE.SUCCESS) {
                    if (r == GpCom.ERROR_CODE.DEVICE_ALREADY_OPEN) {
                        mPortParam.setPortOpenState(true);
                    } else {
                        ToastUtil.showShortToast(GpCom.getErrorText(r));
                    }
                }
            } else {
                ToastUtil.showShortToast("端口参数错误");
            }
        } else {
            Log.d("wolf", "连接打印机:DisconnectToDevice");
            try {
                mGpService.closePort(0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 设备连接方式
     *
     * @param param
     * @return
     */
    private Boolean CheckPortParamters(PortParameters param) {
        boolean rel = false;
        int type = param.getPortType();
        if (type == PortParameters.BLUETOOTH) {
            if (!param.getBluetoothAddr().equals("")) {
                rel = true;
            }
        } else if (type == PortParameters.ETHERNET) {
            if ((!param.getIpAddr().equals("")) && (param.getPortNumber() != 0)) {
                rel = true;
            }
        } else if (type == PortParameters.USB) {
            if (!param.getUsbDeviceName().equals("")) {
                rel = true;
            }
        }
        return rel;
    }

    /**
     * 打印机连接广播
     */
    private BroadcastReceiver PrinterStatusBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (GpCom.ACTION_CONNECT_STATUS.equals(intent.getAction())) {
                int type = intent.getIntExtra(GpPrintService.CONNECT_STATUS, 0);
                Log.e("wolf", "打印机连接广播:connect status " + type);
                if (type == GpDevice.STATE_CONNECTING) {
                    Log.e("wolf", "wolf connect status " + "STATE_NONE" + "连接中");
                    mPortParam.setPortOpenState(false);
                } else if (type == GpDevice.STATE_NONE) {
                    Log.e("wolf", "wolf connect status " + "STATE_NONE" + "连接断开");
                    mPortParam.setPortOpenState(false);
                } else if (type == GpDevice.STATE_VALID_PRINTER) {
                    Log.e("wolf", "wolf connect status " + "STATE_VALID_PRINTER" + "有效的打印机");
                    mPortParam.setPortOpenState(true);
                    ToastUtil.showShortToast("连接好了，可以打印。");
                } else if (type == GpDevice.STATE_INVALID_PRINTER) {
                    Log.e("wolf", "wolf connect status " + "STATE_INVALID_PRINTER" + "无效的打印机");
                }
            }
        }
    };

    /**
     * 打印机状态广播
     */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e("wolf", "打印机状态:" + action);
            // GpCom.ACTION_DEVICE_REAL_STATUS 为广播的IntentFilter
            if (action.equals(GpCom.ACTION_DEVICE_REAL_STATUS)) {

                // 业务逻辑的请求码，对应哪里查询做什么操作
                int requestCode = intent.getIntExtra(GpCom.EXTRA_PRINTER_REQUEST_CODE, -1);
                // 判断请求码，是则进行业务操作
                if (requestCode == MAIN_QUERY_PRINTER_STATUS) {

                    int status = intent.getIntExtra(GpCom.EXTRA_PRINTER_REAL_STATUS, 16);
                    String str;
                    if (status == GpCom.STATE_NO_ERR) {
                        str = "打印机正常";
                    } else {
                        str = "打印机 ";
                        if ((byte) (status & GpCom.STATE_OFFLINE) > 0) {
                            str += "脱机";
                        }
                        if ((byte) (status & GpCom.STATE_PAPER_ERR) > 0) {
                            str += "缺纸";
                        }
                        if ((byte) (status & GpCom.STATE_COVER_OPEN) > 0) {
                            str += "打印机开盖";
                        }
                        if ((byte) (status & GpCom.STATE_ERR_OCCURS) > 0) {
                            str += "打印机出错";
                        }
                        if ((byte) (status & GpCom.STATE_TIMES_OUT) > 0) {
                            str += "查询超时";
                        }
                    }

                    ToastUtil.showShortToast("打印机：" + mPrinterIndex + " 状态：");
                } else if (requestCode == REQUEST_PRINT_LABEL) {
                    int status = intent.getIntExtra(GpCom.EXTRA_PRINTER_REAL_STATUS, 16);
                    if (status == GpCom.STATE_NO_ERR) {
//                        sendLabel();
                        Log.e("wolf", "broadcast:" + "位置1");
                    } else {
                        ToastUtil.showShortToast("query printer status error");
                    }
                } else if (requestCode == REQUEST_PRINT_RECEIPT) {
                    int status = intent.getIntExtra(GpCom.EXTRA_PRINTER_REAL_STATUS, 16);
                    if (status == GpCom.STATE_NO_ERR) {
//                        sendReceipt();
                        printTicket();
                        Log.e("wolf", "broadcast:" + "位置2");
                    } else {
                        ToastUtil.showShortToast("query printer status error");
                    }
                }
            } else if (action.equals(GpCom.ACTION_RECEIPT_RESPONSE)) {
                if (--mTotalCopies > 0) {
//                    sendReceiptWithResponse();
                    Log.e("wolf", "broadcast:" + "位置3");
                }
            } else if (action.equals(GpCom.ACTION_LABEL_RESPONSE)) {
                byte[] data = intent.getByteArrayExtra(GpCom.EXTRA_PRINTER_LABEL_RESPONSE);
                int cnt = intent.getIntExtra(GpCom.EXTRA_PRINTER_LABEL_RESPONSE_CNT, 1);
                String d = new String(data, 0, cnt);
                /**
                 * 这里的d的内容根据RESPONSE_MODE去判断返回的内容去判断是否成功，具体可以查看标签编程手册SET
                 * RESPONSE指令
                 * 该sample中实现的是发一张就返回一次,这里返回的是{00,00001}。这里的对应{Status,######,ID}
                 * 所以我们需要取出STATUS
                 */
                Log.e("wolf", "LABEL RESPONSE" + d);

                if (--mTotalCopies > 0 && d.charAt(1) == 0x00) {
//                    sendLabelWithResponse();
                    Log.e("wolf", "broadcast:" + "位置4");
                }
            }
        }
    };

    /**
     * 注册实时状态查询广播
     */
    private void registerReceiver() {
        context.registerReceiver(mBroadcastReceiver, new IntentFilter(GpCom.ACTION_DEVICE_REAL_STATUS));
        /**
         * 票据模式下，可注册该广播，在需要打印内容的最后加入addQueryPrinterStatus()，在打印完成后会接收到
         * action为GpCom.ACTION_DEVICE_STATUS的广播，特别用于连续打印，
         * 可参照该sample中的sendReceiptWithResponse方法与广播中的处理
         **/
        context.registerReceiver(mBroadcastReceiver, new IntentFilter(GpCom.ACTION_RECEIPT_RESPONSE));
        /**
         * 标签模式下，可注册该广播，在需要打印内容的最后加入addQueryPrinterStatus(RESPONSE_MODE mode)
         * ，在打印完成后会接收到，action为GpCom.ACTION_LABEL_RESPONSE的广播，特别用于连续打印，
         * 可参照该sample中的sendLabelWithResponse方法与广播中的处理
         **/
        context.registerReceiver(mBroadcastReceiver, new IntentFilter(GpCom.ACTION_LABEL_RESPONSE));

        //连接广播
        IntentFilter filter = new IntentFilter();
        filter.addAction(GpCom.ACTION_CONNECT_STATUS);
        context.registerReceiver(PrinterStatusBroadcastReceiver, filter);
    }

    /**
     * 打印东西
     * 通过发广播实现
     */
    public void printReceiptClicked() {
        try {
            int type = mGpService.getPrinterCommandType(mPrinterIndex);
            if (type == GpCom.ESC_COMMAND) {
                //发送打印广播
                mGpService.queryPrinterStatus(mPrinterIndex, 1000, REQUEST_PRINT_RECEIPT);
            } else {
                ToastUtil.showShortToast("Printer is not receipt mode");
            }
        } catch (RemoteException e1) {
            e1.printStackTrace();
        }
    }

    /**
     * 我的测试打印内容
     */
    private void printTicket() {
        EscCommand esc = new EscCommand();
        esc.addInitializePrinter();
        esc.addPrintAndFeedLines((byte) 3);
        /* 打印一维条码 */
        esc.addSelectPrintingPositionForHRICharacters(EscCommand.HRI_POSITION.BELOW);//
        // 设置条码可识别字符位置在条码下方
        esc.addSetBarcodeHeight((byte) 60); // 设置条码高度为60点
        esc.addSetBarcodeWidth((byte) 1); // 设置条码单元宽度为1
        esc.addCODE128(esc.genCodeB("fangwolf")); // 打印Code128码,条码内容

        // 发送数据
        Vector<Byte> datas = esc.getCommand();
        byte[] bytes = GpUtils.ByteTo_byte(datas);
        String sss = Base64.encodeToString(bytes, Base64.DEFAULT);
        int rs;
        try {
            rs = mGpService.sendEscCommand(mPrinterIndex, sss);
            GpCom.ERROR_CODE r = GpCom.ERROR_CODE.values()[rs];
            if (r != GpCom.ERROR_CODE.SUCCESS) {
                ToastUtil.showShortToast(GpCom.getErrorText(r));
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 芝麻开门
     */
    private void openSesame() {
        EscCommand esc = new EscCommand();
        esc.addGeneratePlus(LabelCommand.FOOT.F5, (byte) 255, (byte) 255);

        // 发送数据
        Vector<Byte> datas = esc.getCommand();
        byte[] bytes = GpUtils.ByteTo_byte(datas);
        String sss = Base64.encodeToString(bytes, Base64.DEFAULT);
        int rs;
        try {
            rs = mGpService.sendEscCommand(mPrinterIndex, sss);
            GpCom.ERROR_CODE r = GpCom.ERROR_CODE.values()[rs];
            if (r != GpCom.ERROR_CODE.SUCCESS) {
                ToastUtil.showShortToast(GpCom.getErrorText(r));
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解绑
     */
    public void destroyPrinter() {
        try {
            if (conn != null) {
                context.unbindService(conn);
            }
            context.unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class ToastUtil {
        public static void showShortToast(String s) {
            Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
        }
    }
}
