package ru.livetex.sdk.logic;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;
import io.reactivex.Single;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import okio.ByteString;
import ru.livetex.sdk.entity.AttributesEntity;
import ru.livetex.sdk.entity.AttributesRequest;
import ru.livetex.sdk.entity.BaseEntity;
import ru.livetex.sdk.entity.DialogState;
import ru.livetex.sdk.entity.EmployeeTypingEvent;
import ru.livetex.sdk.entity.HistoryEntity;
import ru.livetex.sdk.entity.SentMessage;
import ru.livetex.sdk.entity.TextMessage;
import ru.livetex.sdk.entity.TypingEvent;
import ru.livetex.sdk.network.NetworkManager;

// todo: interface
public class LiveTexMessagesHandler {

	private final String TAG = "MessagesHandler";

	private final PublishSubject<BaseEntity> entitySubject = PublishSubject.create();
	private final PublishSubject<DialogState> dialogStateSubject = PublishSubject.create();
	private final PublishSubject<HistoryEntity> historySubject = PublishSubject.create();
	private final PublishSubject<EmployeeTypingEvent> employeeTypingSubject = PublishSubject.create();
	private final PublishSubject<AttributesRequest> attributesRequestSubject = PublishSubject.create();
	// todo: clear and dispose on websocket disconnect
	private final HashMap<String, Subject> subscriptions = new HashMap<>();

	// todo: customizable
	private final EntityMapper mapper = new EntityMapper();

	public void onMessage(String text) {
		Log.d(TAG, "onMessage " + text);
		BaseEntity entity = null;

		try {
			entity = mapper.toEntity(text);
		} catch (Exception e) {
			Log.e(TAG, "Error when parsing message", e);
		}
		if (entity == null) {
			return;
		}

		entitySubject.onNext(entity);

		// Subjects to notify client
		if (entity instanceof DialogState) {
			dialogStateSubject.onNext((DialogState) entity);
		} else if (entity instanceof HistoryEntity) {
			historySubject.onNext((HistoryEntity) entity);
		} else if (entity instanceof EmployeeTypingEvent) {
			employeeTypingSubject.onNext((EmployeeTypingEvent) entity);
		} else if (entity instanceof AttributesRequest) {
			attributesRequestSubject.onNext((AttributesRequest) entity);
		}

		Subject subscription = subscriptions.get(entity.correlationId);

		if (subscription != null) {
			subscription.onNext(entity);
			subscription.onComplete();
			subscriptions.remove(entity.correlationId);
		}
	}

	public void sendTypingEvent(String text) {
		TypingEvent event = new TypingEvent(text);
		String json = EntityMapper.gson.toJson(event);
		sendJson(json);
	}

	public void sendAttributes(@Nullable String name,
							   @Nullable String phone,
							   @Nullable String email,
							   @Nullable Map<String, Object> attrs) {
		AttributesEntity event = new AttributesEntity(name, phone, email, attrs);
		String json = EntityMapper.gson.toJson(event);
		sendJson(json);
	}

	// todo: improve handling?
	public Single<SentMessage> sendTextEvent(String text) {
		TextMessage event = new TextMessage(text);
		String json = EntityMapper.gson.toJson(event);

		Subject<SentMessage> subscription = PublishSubject.<SentMessage> create();
		if (NetworkManager.getInstance().getWebSocket() != null) {
			subscriptions.put(event.correlationId, subscription);
			sendJson(json);
		}
		return subscription.take(1).singleOrError();
	}

	public PublishSubject<BaseEntity> entity() {
		return entitySubject;
	}

	public PublishSubject<DialogState> dialogStateUpdate() {
		return dialogStateSubject;
	}

	public PublishSubject<HistoryEntity> history() {
		return historySubject;
	}

	public PublishSubject<EmployeeTypingEvent> employeeTyping() {
		return employeeTypingSubject;
	}

	public PublishSubject<AttributesRequest> attributesRequest() {
		return attributesRequestSubject;
	}

	public void onDataMessage(ByteString bytes) {
		// not used
	}

	private void sendJson(String json) {
		if (NetworkManager.getInstance().getWebSocket() != null) {
			Log.d(TAG, "Sending: " + json);
			NetworkManager.getInstance().getWebSocket().send(json);
		} else {
			throw new IllegalStateException("Trying to send data when websocket is null");
		}
	}
}
