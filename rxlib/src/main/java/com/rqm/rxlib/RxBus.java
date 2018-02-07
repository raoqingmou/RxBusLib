package com.rqm.rxlib;

import android.arch.lifecycle.LifecycleOwner;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

import static com.rqm.rxlib.EventThread.MAIN_THREAD;

/**
 * Created by raoqingmou on 2017/7/15.
 */

public class RxBus {
    private static final String TAG = "RxBus";

    protected static RxBus instance;

    public static RxBus getInstance() {
        if (instance == null) {
            synchronized (RxBus.class) {
                if (instance == null) {
                    instance = new RxBus();
                }
            }
        }
        return instance;
    }

    //发布者
    protected Subject bus;

    //存放订阅者信息
    protected Map<Object, CompositeDisposable> subscriptions = new HashMap<>();

    /**
     * PublishSubject 创建一个可以在订阅之后把数据传输给订阅者Subject
     */
    public RxBus() {
        bus = PublishSubject.create().toSerialized();
    }


    //TAG默认值
    public static final int TAG_DEFAULT = -1000;

    public void post(@NonNull Object obj) {
        post(TAG_DEFAULT, obj);
    }

    /**
     * 发布事件
     *
     * @param code 值使用RxBus.getInstance().getTag(class,value)获取
     * @param obj  为需要被处理的事件
     */
    public void post(@NonNull int code, @NonNull Object obj) {
        bus.onNext(new Msg(code, obj));
    }

    /**
     * 订阅事件
     * 默认事件的执行线程为主线程
     *
     * @return
     */
    public <T> void tObservable(Object subscriber, final int code, final Class<T> eventType, Consumer<T> consumer) {
        tObservable(subscriber, code, eventType, EventThread.getScheduler(MAIN_THREAD), consumer);
    }

    /**
     * 订阅事件
     * 需要指定事件的执行线程(scheduler)
     *
     * @return
     */
    public <T> void tObservable(Object subscriber, final int code, final Class<T> eventType, Scheduler scheduler, Consumer<T> consumer) {
        putSubscriptionsData(subscriber, bus.ofType(Msg.class)//判断接收事件类型
                .filter(new Predicate<Msg>() {
                    @Override
                    public boolean test(Msg msg) throws Exception {
                        return msg.code == code;
                    }
                })
                .map(new Function<Msg, Object>() {
                    @Override
                    public Object apply(Msg msg) throws Exception {
                        return msg.object;
                    }
                })
                .cast(eventType)
                .observeOn(scheduler)
                .subscribe(consumer));
    }

    /**
     * 添加订阅者到map空间来unRegister
     *
     * @param subscriber 订阅者
     * @param disposable 订阅者 Subscription
     */
    public void putSubscriptionsData(Object subscriber, Disposable disposable) {
        CompositeDisposable subs = subscriptions.get(subscriber);
        if (subs == null) {
            subs = new CompositeDisposable();

            // 如果订阅者实现了LifecycleOwner, 就对其添加一个LifecycleObserver来绑定生命周期, 从而实现自动解绑
            if (subscriber instanceof LifecycleOwner) {
                Log.i(TAG, "注册事件生命周期");
                SubscriberLifecycleHolder subscriberLifecycleHolder = new SubscriberLifecycleHolder((LifecycleOwner) subscriber);
                ((LifecycleOwner) subscriber).getLifecycle().addObserver(subscriberLifecycleHolder);
            }else{
                Log.i(TAG, "不是Lifecycle框架, 没注册生命周期");
            }
        }
        subs.add(disposable);
        subscriptions.put(subscriber, subs);
    }

    /**
     * 解除订阅者
     *
     * @param subscriber 订阅者
     */
    public void unRegister(Object subscriber) {
        Log.i(TAG, "unRegister");
        if (subscriber != null) {
            CompositeDisposable compositeDisposable = subscriptions.get(subscriber);
            if (compositeDisposable != null) {
                compositeDisposable.dispose();
                subscriptions.remove(subscriber);
                Log.i(TAG, "unRegister - remove");
            }
        }
        Log.i(TAG, "unRegister-end");
    }

}
