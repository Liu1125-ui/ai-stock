package com.aistock.user.position;
import java.util.*;import java.util.concurrent.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
import com.aistock.common.BusinessException;import static com.aistock.user.position.PositionRecords.*;
@Tag("mysql") class PositionRepositoryMysqlTest {
 @Test void emptyReadDoesNotCreateBook(){try(var f=new PositionTestFixture()){assertEquals("0",f.service.list(f.owner).bookRevision());assertTrue(f.service.list(f.owner).items().isEmpty());assertEquals(0,f.db.jdbc().queryForObject("select count(*) from position_book",Integer.class));}}
 @Test void lifecycleReplayAndVersionConflictsAreDurable(){try(var f=new PositionTestFixture()){
  var input=f.create("SH600030");var created=f.service.apply(f.owner,input);assertEquals("CREATED",created.state());assertEquals(created,f.service.apply(f.owner,input));assertEquals("1",f.service.list(f.owner).bookRevision());
  var same=new PositionMutation(UUID.randomUUID(),"UPDATE",UUID.fromString(created.positionId()),null,"1","20.000000",1L);assertEquals("UNCHANGED",f.service.apply(f.owner,same).state());assertEquals("1",f.service.list(f.owner).bookRevision());
  var change=new PositionMutation(UUID.randomUUID(),"UPDATE",UUID.fromString(created.positionId()),null,"2","21.123456",1L);assertEquals("UPDATED",f.service.apply(f.owner,change).state());
  var stale=new PositionMutation(UUID.randomUUID(),"UPDATE",UUID.fromString(created.positionId()),null,"3","22",1L);assertEquals("REJECTED",f.service.apply(f.owner,stale).state());assertEquals(409,f.service.operation(f.owner,stale.requestId()).httpStatus());
  var deletion=new PositionMutation(UUID.randomUUID(),"DELETE",UUID.fromString(created.positionId()),null,null,null,2L);var receipt=f.service.apply(f.owner,deletion);assertEquals("DELETED",receipt.state());assertEquals(receipt,f.service.apply(f.owner,deletion));assertEquals("3",f.service.list(f.owner).bookRevision());assertTrue(f.service.list(f.owner).items().isEmpty());assertEquals(receipt,f.service.operation(f.owner,deletion.requestId()));
 }}
 @Test void rejectsDuplicateStockOtherOwnerAndConflictingRequest(){try(var f=new PositionTestFixture()){
  var input=f.create("SH600030");var first=f.service.apply(f.owner,input);assertEquals(409,f.service.apply(f.owner,f.create("SH600030")).httpStatus());
  var forged=new PositionMutation(UUID.randomUUID(),"DELETE",UUID.fromString(first.positionId()),null,null,null,1L);assertEquals(404,f.service.apply(f.other,forged).httpStatus());
  assertThrows(BusinessException.class,()->f.service.operation(f.other,input.requestId()));
  var conflict=new PositionMutation(input.requestId(),"CREATE",null,"SH600276","1","20",null);assertThrows(BusinessException.class,()->f.service.apply(f.owner,conflict));
  assertEquals("CREATED",f.service.apply(f.other,input).state());assertEquals(1,f.service.list(f.owner).items().size());
 }}
 @Test void concurrentLastSlotHasOnlyOneWinner()throws Exception{try(var f=new PositionTestFixture()){
  for(int i=0;i<99;i++)assertEquals("CREATED",f.service.apply(f.owner,f.create("SH"+String.format("%06d",i))).state());
  var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
  try{var a=pool.submit(()->{barrier.await();return f.service.apply(f.owner,f.create("SH600030"));});var b=pool.submit(()->{barrier.await();return f.service.apply(f.owner,f.create("SH600276"));});var outcomes=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));assertEquals(1,outcomes.stream().filter(x->x.state().equals("CREATED")).count());assertEquals(100,f.service.list(f.owner).items().size());assertEquals("100",f.service.list(f.owner).bookRevision());}finally{pool.shutdownNow();}
 }}
 @Test void sameRevisionConcurrentUpdatesCannotOverwrite()throws Exception{try(var f=new PositionTestFixture()){
  var first=f.service.apply(f.owner,f.create("SH600030"));var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
  try{var jobs=new ArrayList<Future<PositionReceipt>>();for(int i=2;i<=3;i++){var qty=""+i;jobs.add(pool.submit(()->{barrier.await();return f.service.apply(f.owner,new PositionMutation(UUID.randomUUID(),"UPDATE",UUID.fromString(first.positionId()),null,qty,"20",1L));}));}var out=List.of(jobs.get(0).get(10,TimeUnit.SECONDS),jobs.get(1).get(10,TimeUnit.SECONDS));assertEquals(1,out.stream().filter(x->x.state().equals("UPDATED")).count());assertEquals("2",f.service.list(f.owner).bookRevision());}finally{pool.shutdownNow();}
 }}
 @Test void receiptFailureRollsBackMutationAndBook(){try(var f=new PositionTestFixture()){
  f.db.jdbc().execute("CREATE TRIGGER reject_position_receipt BEFORE INSERT ON position_operation_receipt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture rejection'");
  try{assertThrows(RuntimeException.class,()->f.service.apply(f.owner,f.create("SH600030")));assertTrue(f.service.list(f.owner).items().isEmpty());assertEquals("0",f.service.list(f.owner).bookRevision());assertEquals(0,f.db.jdbc().queryForObject("select count(*) from position_operation_receipt",Integer.class));}finally{f.db.jdbc().execute("DROP TRIGGER reject_position_receipt");}
 }}
}
