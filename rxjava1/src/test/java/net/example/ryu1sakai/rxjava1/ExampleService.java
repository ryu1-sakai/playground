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
            if (id == Integer.MAX_VALUE) {
                return Observable.empty();
            }
            return Observable.just(new Foo(id));
        }
    }

    private interface FooService {
        Observable<Foo> getFoo(int userId);
    }

    private static class SimpleFooService implements FooService {
        private UserStorage userStorage = new UserStorage();
        private FooStorage fooStorage = new FooStorage();

        @Override
        public Observable<Foo> getFoo(int userId) {
            if (userId <= 0) {
                return Observable.error(new Exception("Invalid user ID"));
            }
            return userStorage.getFooId(userId)
                    .switchIfEmpty(Observable.error(new Exception("User not found")))
                    .flatMap(fooStorage::getFoo)
                    .switchIfEmpty(Observable.error(new Exception("Foo not found")));
        }
    }

    private static class VerboseFooService implements FooService {
        private UserStorage userStorage = new UserStorage();
        private FooStorage fooStorage = new FooStorage();

        @Override
        public Observable<Foo> getFoo(int userId) {
            if (userId <= 0) {
                return Observable.error(new Exception("Invalid user ID " + userId));
            }
            return userStorage.getFooId(userId)
                    .switchIfEmpty(Observable.error(new Exception(String.format("User %d not found", userId))))
                    .flatMap(fid -> {
                        String emptyError = String.format("Foo %d not found", fid);
                        return fooStorage.getFoo(fid).switchIfEmpty(Observable.error(new Exception(emptyError)));
                    });
        }
    }

    private static class FooServer {
        private final FooService simpleFooService = new SimpleFooService();
        private final FooService verboseFooService = new VerboseFooService();
        private final Subscriber<Response> subscriber;

        private FooServer(Subscriber<Response> subscriber) {
            this.subscriber = subscriber;
        }

        private void getFoo(FooService service, Request request) {
            service.getFoo(request.getUserId())
                    .map(NormalResponse::new)
                    .ofType(Response.class)
                    .onErrorReturn(e -> new ErrorResponse(e.getMessage()))
                    .switchIfEmpty(Observable.just(new ErrorResponse("Unknown error")))
                    .subscribe(subscriber::onNext);
        }

        private void getFooSimple(Request request) {
            getFoo(simpleFooService, request);
        }

        private void getFooVerbose(Request request) {
            getFoo(verboseFooService, request);
        }
    }

    public static void main(String[] args) {
        TestSubscriber<Response> subscriber = new TestSubscriber<>();
        FooServer server = new FooServer(subscriber);

        server.getFooSimple(new Request(100));
        server.getFooSimple(new Request(-1));
        server.getFooVerbose(new Request(Integer.MAX_VALUE));
        subscriber.assertValues(new NormalResponse(new Foo(100)), new ErrorResponse("Invalid user ID"),
                new ErrorResponse(String.format("Foo %d not found", Integer.MAX_VALUE)));
    }
}
