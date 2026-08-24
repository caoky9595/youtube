import type { ReactNode } from 'react'
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import { restrictToParentElement, restrictToVerticalAxis } from '@dnd-kit/modifiers'
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'

/**
 * Danh sach keo-tha de doi thu tu.
 *
 * Truoc day doi thu tu bang nut ▲▼ tung buoc mot: dua mot video tu vi tri 20
 * len dau la 19 lan bam. Keo-tha lam duoc trong mot dong tac, va chay ca tren
 * dien thoai (PointerSensor xu ly ca chuot lan cam ung).
 *
 * Van giu dieu khien bang ban phim: focus vao tay keo roi dung mui ten.
 */
export function SortableList({
  ids,
  onReorder,
  children,
}: {
  ids: string[]
  /** Nhan thu tu MOI day du, de goi thang RPC reorder_*. */
  onReorder: (ids: string[]) => void
  children: ReactNode
}) {
  const sensors = useSensors(
    // Phai keo 6px moi tinh la keo — khong thi moi cu bam vao nut trong hang
    // deu bi hieu thanh keo.
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  function handleEnd(event: DragEndEvent) {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const from = ids.indexOf(String(active.id))
    const to = ids.indexOf(String(over.id))
    if (from < 0 || to < 0) return
    onReorder(arrayMove(ids, from, to))
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      modifiers={[restrictToVerticalAxis, restrictToParentElement]}
      onDragEnd={handleEnd}
    >
      <SortableContext items={ids} strategy={verticalListSortingStrategy}>
        {children}
      </SortableContext>
    </DndContext>
  )
}

/** Mot hang keo duoc. Tay keo la phan tu do `handle` tra ve. */
export function SortableRow({
  id,
  children,
}: {
  id: string
  children: (handle: ReactNode, dragging: boolean) => ReactNode
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id,
  })

  const handle = (
    <button
      type="button"
      {...attributes}
      {...listeners}
      title="Kéo để đổi thứ tự"
      aria-label="Kéo để đổi thứ tự"
      className="cursor-grab touch-none select-none px-1 text-yt-dim hover:text-white active:cursor-grabbing"
    >
      ⠿
    </button>
  )

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={isDragging ? 'relative z-20 opacity-90' : undefined}
    >
      {children(handle, isDragging)}
    </div>
  )
}
