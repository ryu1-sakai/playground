package net.example.ryu1sakai.rxjava1;

import lombok.Value;
import rx.Observable;
import rx.Subscriber;
import rx.observers.TestSubscriber;

public class ExampleService {
    @Value
    private static class Foo {
        private int id;
    }

    @Value
    private static class Request {
        private int userId;
    }

    private interface Response {}

    @Value
    private static class NormalResponse implements Response {
        private Foo foo;
    }

    @Value
    private static class ErrorResponse implements Response {
        private String message;
    }

    private static class UserStorage {
        private Observable<Integer> getFooId(int userId) {
            return Observable.just(userId);
        }
    }

    private static class FooStorage {
        private Observable<Foo> getFoo(int id) {
            return Observable.just(new Foo(id));
        }
    }

    private static class FooService {
        private UserStorage userStorage = new UserStorage();
        private FooStorage fooStorage = new FooStorage();

        private Observable<Foo> getFoo(int userId) {
            if (userId <= 0) {
                return Observable.error(new Exception("Invalid user ID"));
            }
            return userStorage.getFooId(userId)
                    .switchIfEmpty(Observable.error(new Exception("User not found")))
                    .flatMap(fooStorage::getFoo)
                    .switchIfEmpty(Observable.error(new Exception("Foo not found")));
        }
    }

    private static class FooServer {
        private final FooService fooService = new FooService();
        private final Subscriber<Response> subscriber;

        private FooServer(Subscriber<Response> subscriber) {
            this.subscriber = subscriber;
        }

        private void getFoo(Request request) {
            fooService.getFoo(request.getUserId())
                    .map(NormalResponse::new)
                    .ofType(Response.class)
                    .onErrorReturn(e -> new ErrorResponse(e.getMessage()))
                    .switchIfEmpty(Observable.just(new ErrorResponse("Unknown error")))
                    .subscribe(subscriber::onNext);
        }
    }

    public static void main(String[] args) {
        TestSubscriber<Response> subscriber = new TestSubscriber<>();
        FooServer server = new FooServer(subscriber);

        server.getFoo(new Request(100));
        server.getFoo(new Request(-1));
        subscriber.assertValues(new NormalResponse(new Foo(100)), new ErrorResponse("Invalid user ID"));
    }
}
